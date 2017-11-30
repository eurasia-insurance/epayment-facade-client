package tech.lapsa.epayment.facade.beans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import tech.lapsa.epayment.dao.QazkomOrderDAO;
import tech.lapsa.epayment.dao.QazkomPaymentDAO;
import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.QazkomOrder;
import tech.lapsa.epayment.domain.QazkomPayment;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.PaymentMethod;
import tech.lapsa.epayment.facade.PaymentMethod.Http;
import tech.lapsa.epayment.facade.QazkomFacade;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.java.commons.function.MyMaps;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.io.MyFiles;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.java.commons.security.MyCertificates;
import tech.lapsa.java.commons.security.MyKeyStores;
import tech.lapsa.java.commons.security.MyKeyStores.StoreType;
import tech.lapsa.java.commons.security.MyPrivateKeys;

@Stateless
public class QazkomFacadeBean implements QazkomFacade {

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(QazkomFacade.class) //
	    // .addWithPrefix("QAZKOM") //
	    // .addWithCAPS() //
	    .build();

    @Resource(lookup = QazkomConstants.JNDI_QAZKOM_CONFIG)
    private Properties qazkomConfig;

    @Inject
    private QazkomOrderDAO qoDAO;

    @Inject
    private QazkomPaymentDAO qpDAO;

    private QazkomSettings qazkomSettings;

    private static class QazkomSettings {

	final URI QAZKOM_EPAY_URI;
	final String QAZKOM_EPAY_HTTP_METHOD;

	final String QAZKOM_EPAY_TEMPLATE;

	final String QAZKOM_MERCHANT_ID;
	final String QAZKOM_MERCHANT_NAME;
	final PrivateKey QAZKOM_MERCHANT_key;
	final X509Certificate QAZKOM_MERCHANT_CERTIFICATE;

	final X509Certificate QAZKOM_BANK_CERTIFICATE;

	private QazkomSettings(Properties qazkomConfig) {

	    try {
		QAZKOM_EPAY_URI = new URI(qazkomConfig.getProperty(QazkomConstants.PROPERTY_BANK_EPAY_URL));
		MyObjects.requireNonNull(QAZKOM_EPAY_URI, "QAZKOM_EPAY_URI");
	    } catch (URISyntaxException e) {
		throw new RuntimeException("Qazkom Epay URI process failed", e);
	    }

	    {
		QAZKOM_EPAY_HTTP_METHOD = "POST";
	    }

	    {
		QAZKOM_EPAY_TEMPLATE = qazkomConfig.getProperty(QazkomConstants.PROPERTY_BANK_EPAY_TEMPLATE);
		MyStrings.requireNonEmpty(QAZKOM_EPAY_TEMPLATE, "QAZKOM_EPAY_TEMPLATE");
	    }

	    {
		QAZKOM_MERCHANT_ID = qazkomConfig.getProperty(QazkomConstants.PROPERTY_MERCHANT_ID);
		MyStrings.requireNonEmpty(QAZKOM_MERCHANT_ID, "QAZKOM_MERCHANT_ID");

		QAZKOM_MERCHANT_NAME = qazkomConfig.getProperty(QazkomConstants.PROPERTY_MERCHANT_NAME);
		MyStrings.requireNonEmpty(QAZKOM_MERCHANT_NAME, "QAZKOM_MERCHANT_NAME");

	    }

	    {
		final String KEYSTORE_FILE = qazkomConfig.getProperty(QazkomConstants.PROPERTY_MERCHANT_KEYSTORE_FILE);
		MyStrings.requireNonEmpty(KEYSTORE_FILE, "KEYSTORE_FILE");

		try (final InputStream KEYSTORE_FILE_STREAM = MyFiles.optionalAsStream(KEYSTORE_FILE)//
			.orElseThrow(() -> new RuntimeException("Keystore not found"))) {

		    final String KEYSTORE_TYPE_S = qazkomConfig
			    .getProperty(QazkomConstants.PROPERTY_MERCHANT_KEYSTORE_TYPE);
		    MyStrings.requireNonEmpty(KEYSTORE_TYPE_S, "KEYSTORE_TYPE_S");
		    final StoreType KEYSTORE_TYPE = StoreType.valueOf(KEYSTORE_TYPE_S);
		    MyObjects.requireNonNull(KEYSTORE_TYPE, "KEYSTORE_TYPE");

		    final String KEYSTORE_PASSWORD = qazkomConfig
			    .getProperty(QazkomConstants.PROPERTY_MERCHANT_KEYSTORE_PASSWORD);
		    MyStrings.requireNonEmpty(KEYSTORE_PASSWORD, "KEYSTORE_PASSWORD");

		    final KeyStore KEYSTORE = MyKeyStores.from(KEYSTORE_FILE_STREAM, KEYSTORE_TYPE, KEYSTORE_PASSWORD) //
			    .orElseThrow(() -> new RuntimeException("Can not load keystore"));

		    final String MERCHANT_ALIAS = qazkomConfig
			    .getProperty(QazkomConstants.PROPERTY_MERCHANT_KEYSTORE_KEYALIAS);
		    MyStrings.requireNonEmpty(MERCHANT_ALIAS, "MERCHANT_ALIAS");

		    QAZKOM_MERCHANT_key = MyPrivateKeys.from(KEYSTORE, MERCHANT_ALIAS, KEYSTORE_PASSWORD) //
			    .orElseThrow(() -> new RuntimeException("Can't find key entry"));

		    QAZKOM_MERCHANT_CERTIFICATE = MyCertificates.from(KEYSTORE, MERCHANT_ALIAS) //
			    .orElseThrow(() -> new RuntimeException("Can find key entry"));
		} catch (IOException e) {
		    throw new RuntimeException(e);
		}
	    }

	    {
		final String KEYSTORE_FILE = qazkomConfig.getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_FILE);
		MyStrings.requireNonEmpty(KEYSTORE_FILE, "KEYSTORE_FILE");

		try (final InputStream KEYSTORE_FILE_STREAM = MyFiles.optionalAsStream(KEYSTORE_FILE) //
			.orElseThrow(() -> new RuntimeException("Keystore not found"))) {

		    final String KEYSTORE_TYPE_S = qazkomConfig
			    .getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_TYPE);
		    MyStrings.requireNonEmpty(KEYSTORE_TYPE_S, "KEYSTORE_TYPE_S");
		    final StoreType KEYSTORE_TYPE = StoreType.valueOf(KEYSTORE_TYPE_S);
		    MyObjects.requireNonNull(KEYSTORE_TYPE, "KEYSTORE_TYPE");

		    final String KEYSTORE_PASSWORD = qazkomConfig
			    .getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_PASSWORD);
		    MyStrings.requireNonEmpty(KEYSTORE_PASSWORD, "KEYSTORE_PASSWORD");

		    final KeyStore KEYSTORE = MyKeyStores.from(KEYSTORE_FILE_STREAM, KEYSTORE_TYPE, KEYSTORE_PASSWORD) //
			    .orElseThrow(() -> new RuntimeException("Can not load keystore"));

		    final String BANK_ALIAS = qazkomConfig
			    .getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_CERTALIAS);
		    MyStrings.requireNonEmpty(BANK_ALIAS, "BANK_ALIAS");

		    QAZKOM_BANK_CERTIFICATE = MyCertificates.from(KEYSTORE, BANK_ALIAS) //
			    .orElseThrow(() -> new RuntimeException("Can find cert entry"));
		} catch (IOException e) {
		    throw new RuntimeException(e);
		}
	    }
	}
    }

    @PostConstruct
    public void init() {
	this.qazkomSettings = new QazkomSettings(qazkomConfig);
    }

    @Inject
    private EpaymentFacade epayments;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public QazkomPayment processPayment(final String responseXml) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {

	    MyObjects.requireNonNull(qazkomSettings, "qazkomSettings");
	    MyStrings.requireNonEmpty(responseXml, "responseXml");

	    logger.INFO.log("New response '%1$s'", responseXml);

	    try {

		final QazkomPayment qp;
		{
		    final QazkomPayment qpp = QazkomPayment.builder() //
			    .fromRawXml(responseXml) //
			    .withBankCertificate(qazkomSettings.QAZKOM_BANK_CERTIFICATE) //
			    .build();
		    qp = qpDAO.save(qpp);
		    if (!qpDAO.isUniqueNumber(qpp.getOrderNumber()))
			throw MyExceptions.illegalStateFormat("Already processed QazkomPayment with order number %1$s",
				qpp.getOrderNumber());
		}

		logger.INFO.log("QazkomPayment OK - '%1$s'", qp);

		final QazkomOrder qo = qoDAO.optionalByNumber(qp.getOrderNumber()) //
			.orElseThrow(illegalArgumentSupplierFormat(
				"No QazkomOrder found or reference is invlaid - '%1$s'", qp.getOrderNumber()));
		logger.INFO.log("QazkomOrder OK - '%1$s'", qo);

		qo.paidBy(qp);
		qoDAO.save(qo);

		final Invoice i = qo.optionalForInvoice() //
			.orElseThrow(illegalStateSupplierFormat("No Invoice attached - '%1$s'", qo));
		logger.INFO.log("Invoice OK - '%1$s'", i);

		epayments.invoiceHasPaidBy(i, qpDAO.save(qp));

		return qp;
	    } catch (IllegalArgumentException | IllegalStateException e) {
		logger.WARN.log(e, "Error processing response : %1$s", e.getMessage());
		throw e;
	    }
	});
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PaymentMethod httpMethod(URI postbackURI, URI returnUri, Invoice forInvoice)
	    throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {
	    MyObjects.requireNonNull(postbackURI, "postbackURI");
	    MyObjects.requireNonNull(returnUri, "returnUri");
	    MyObjects.requireNonNull(forInvoice, "forInvoice");

	    QazkomOrder o = qoDAO.optionalLatestForInvoice(forInvoice) //
		    .orElseGet(() -> {
			return qoDAO.save(QazkomOrder.builder() //
				.forInvoice(forInvoice) //
				.withGeneratedNumber(qoDAO::isUniqueNumber) //
				.withMerchant(qazkomSettings.QAZKOM_MERCHANT_ID, //
					qazkomSettings.QAZKOM_MERCHANT_NAME, //
					qazkomSettings.QAZKOM_MERCHANT_CERTIFICATE, //
					qazkomSettings.QAZKOM_MERCHANT_key) //
				.build());
		    });

	    Http http = new Http(qazkomSettings.QAZKOM_EPAY_URI, qazkomSettings.QAZKOM_EPAY_HTTP_METHOD,
		    MyMaps.of(
			    "Signed_Order_B64", MyStrings.requireNonEmpty(o.getOrderDoc().getBase64Xml(), "content"), //
			    "template", qazkomSettings.QAZKOM_EPAY_TEMPLATE, //
			    "email", forInvoice.optionalConsumerEmail().orElse(""), //
			    "PostLink", postbackURI.toString(),
			    "Language", forInvoice.getConsumerPreferLanguage().getTag(), //
			    "appendix", o.getCartDoc().getBase64Xml(), //
			    "BackLink", returnUri.toString() //
	    ));
	    return new QazkomPaymentMethod(http);
	});
    }

    final class QazkomPaymentMethod implements PaymentMethod {

	private static final long serialVersionUID = 1L;

	final Http http;

	private QazkomPaymentMethod(final Http http) {
	    this.http = MyObjects.requireNonNull(http, "http");
	}

	@Override
	public Http getHttp() {
	    return http;
	}
    }
}
