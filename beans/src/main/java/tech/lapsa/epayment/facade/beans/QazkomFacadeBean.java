package tech.lapsa.epayment.facade.beans;

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
import javax.inject.Inject;

import tech.lapsa.epayment.dao.InvoiceDAO;
import tech.lapsa.epayment.dao.QazkomOrderDAO;
import tech.lapsa.epayment.dao.QazkomPaymentDAO;
import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.QazkomOrder;
import tech.lapsa.epayment.domain.QazkomPayment;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.PaymentMethod;
import tech.lapsa.epayment.facade.PaymentMethod.Http;
import tech.lapsa.epayment.facade.QazkomFacade;
import tech.lapsa.java.commons.function.MyMaps;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.java.commons.resources.Resources;
import tech.lapsa.java.commons.security.MyCertificates;
import tech.lapsa.java.commons.security.MyKeyStores;
import tech.lapsa.java.commons.security.MyKeyStores.StoreType;
import tech.lapsa.java.commons.security.MyPrivateKeys;

@Stateless
public class QazkomFacadeBean implements QazkomFacade {

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(QazkomFacade.class) //
	    .addWithPrefix("QAZKOM") //
	    .addWithCAPS() //
	    .build();

    @Resource(lookup = QazkomConstants.JNDI_QAZKOM_CONFIG)
    private Properties qazkomConfig;

    @Inject
    private QazkomOrderDAO qoDAO;
    @Inject
    private InvoiceDAO invoiceDAO;
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

		final String KEYSTORE_FILE = qazkomConfig.getProperty(QazkomConstants.PROPERTY_MERCHANT_KEYSTORE_FILE);
		MyStrings.requireNonEmpty(KEYSTORE_FILE, "KEYSTORE_FILE");
		final InputStream KEYSTORE_FILE_STREAM = Resources.optionalAsStream(this.getClass(), KEYSTORE_FILE) //
			.orElseThrow(() -> new RuntimeException("Keystore not found"));

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

	    }

	    {
		final String KEYSTORE_FILE = qazkomConfig.getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_FILE);
		MyStrings.requireNonEmpty(KEYSTORE_FILE, "KEYSTORE_FILE");
		final InputStream KEYSTORE_FILE_STREAM = Resources.optionalAsStream(this.getClass(), KEYSTORE_FILE) //
			.orElseThrow(() -> new RuntimeException("Keystore not found"));

		final String KEYSTORE_TYPE_S = qazkomConfig.getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_TYPE);
		MyStrings.requireNonEmpty(KEYSTORE_TYPE_S, "KEYSTORE_TYPE_S");
		final StoreType KEYSTORE_TYPE = StoreType.valueOf(KEYSTORE_TYPE_S);
		MyObjects.requireNonNull(KEYSTORE_TYPE, "KEYSTORE_TYPE");

		final String KEYSTORE_PASSWORD = qazkomConfig
			.getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_PASSWORD);
		MyStrings.requireNonEmpty(KEYSTORE_PASSWORD, "KEYSTORE_PASSWORD");

		final KeyStore KEYSTORE = MyKeyStores.from(KEYSTORE_FILE_STREAM, KEYSTORE_TYPE, KEYSTORE_PASSWORD) //
			.orElseThrow(() -> new RuntimeException("Can not load keystore"));

		final String BANK_ALIAS = qazkomConfig.getProperty(QazkomConstants.PROPERTY_BANK_CERTSTORE_CERTALIAS);
		MyStrings.requireNonEmpty(BANK_ALIAS, "BANK_ALIAS");

		QAZKOM_BANK_CERTIFICATE = MyCertificates.from(KEYSTORE, BANK_ALIAS) //
			.orElseThrow(() -> new RuntimeException("Can find cert entry"));

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
    public Invoice handleResponse(String responseXml) throws IllegalArgumentException, IllegalStateException {
	MyObjects.requireNonNull(qazkomSettings, "qazkomSettings");
	MyStrings.requireNonEmpty(responseXml, "responseXml");

	logger.INFO.log("New response '%1$s'", responseXml);

	QazkomPayment qp;
	QazkomOrder qo;
	Invoice i;

	try {
	    logger.INFO.log("Validating response...");
	    qp = QazkomPayment.builder() //
		    .fromRawXml(responseXml) //
		    .withBankCertificate(qazkomSettings.QAZKOM_BANK_CERTIFICATE) //
		    .build();

	    qo = qoDAO.optionalByNumber(qp.getOrderNumber()) //
		    .orElseThrow(() -> new IllegalArgumentException("No payment order found or reference is invlaid"));

	    qo.validate(qp);

	    i = qo.getForInvoice();
	    MyObjects.requireNonNull(i, "invoice");
	    logger.INFO.log("Invoice number is %1$s for amount %2$.2d", i.getNumber(), i.getAmount());

	    logger.INFO.log("Validated OK");
	} catch (IllegalArgumentException | IllegalStateException e) {
	    logger.WARN.log(e, "Error validating response : %1$s", e.getMessage());
	    throw e;
	}

	try {
	    logger.INFO.log("Processing invoice %1$s...", i.getNumber());
	    i.paidBy(qp);
	    qo.paidBy(qp);
	    i = invoiceDAO.save(i);
	    qp = qpDAO.save(qp);
	    qo = qoDAO.save(qo);
	    epayments.markPaid(i);
	    logger.INFO.log("Processed OK");
	} catch (IllegalArgumentException | IllegalStateException e) {
	    logger.WARN.log(e, "Error processing invoice : %1$s", e.getMessage());
	    throw e;
	}

	logger.INFO.log("HANDLED SUCCESSFULY invoice number = '%1$s'", i.getNumber());
	return i;
    }

    @Override
    public PaymentMethod httpMethod(URI postbackURI, URI returnUri, Invoice forInvoice) {
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
			"email", forInvoice.getConsumerEmail(), //
			"PostLink", postbackURI.toString(),
			"Language", forInvoice.getConsumerPreferLanguage().getTag(), //
			"appendix", o.getCartDoc().getBase64Xml(), //
			"BackLink", returnUri.toString() //
		));
	return new QazkomPaymentMethod(http);

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
