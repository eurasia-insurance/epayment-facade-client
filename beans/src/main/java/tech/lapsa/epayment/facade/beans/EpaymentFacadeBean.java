package tech.lapsa.epayment.facade.beans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Currency;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import tech.lapsa.epayment.dao.InvoiceDAO;
import tech.lapsa.epayment.dao.PaymentDAO;
import tech.lapsa.epayment.dao.QazkomErrorDAO;
import tech.lapsa.epayment.dao.QazkomOrderDAO;
import tech.lapsa.epayment.dao.QazkomPaymentDAO;
import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.domain.Payment;
import tech.lapsa.epayment.domain.QazkomError;
import tech.lapsa.epayment.domain.QazkomOrder;
import tech.lapsa.epayment.domain.QazkomPayment;
import tech.lapsa.epayment.domain.UnknownPayment;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.NotificationFacade;
import tech.lapsa.epayment.facade.NotificationFacade.Notification;
import tech.lapsa.epayment.facade.NotificationFacade.Notification.NotificationChannel;
import tech.lapsa.epayment.facade.NotificationFacade.Notification.NotificationEventType;
import tech.lapsa.epayment.facade.NotificationFacade.Notification.NotificationRecipientType;
import tech.lapsa.epayment.facade.PaymentMethod;
import tech.lapsa.epayment.facade.PaymentMethod.Http;
import tech.lapsa.epayment.shared.entity.XmlInvoiceHasPaidEvent;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.java.commons.function.MyMaps;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyOptionals;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.javax.jms.client.JmsDestination;
import tech.lapsa.javax.jms.client.JmsEventNotificatorClient;

@Stateless
public class EpaymentFacadeBean implements EpaymentFacade {

    static final String JNDI_CONFIG = "epayment/resource/Configuration";
    static final String PROPERTY_DEFAULT_PAYMENT_URI_PATTERN = "default-payment-uri.pattern";

    private QazkomSettings qazkomSettings;

    @Resource(lookup = QazkomSettings.JNDI_QAZKOM_CONFIG)
    private Properties qazkomConfig;

    @PostConstruct
    public void init() {
	qazkomSettings = new QazkomSettings(qazkomConfig);
    }

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public URI getDefaultPaymentURI(String invoiceNumber) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> _getDefaultPaymentURI(invoiceNumber));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Invoice getInvoiceByNumber(final String invoiceNumber) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> _invoiceByNumber(invoiceNumber));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public boolean hasInvoiceWithNumber(final String invoiceNumber) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> _hasInvoiceWithNumber(invoiceNumber));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PaymentMethod qazkomHttpMethod(final URI postbackURI, final URI failureURI, final URI returnURI,
	    final Invoice forInvoice) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> _qazkomHttpMethod(postbackURI, failureURI, returnURI, forInvoice));
    }

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String invoiceAccept(final InvoiceBuilder builder) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> _invoiceAccept(builder));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void completeWithUnknownPayment(final String invoiceNumber, Double paidAmount, Currency paidCurency,
	    Instant paidInstant, String paidReference) throws IllegalArgument, IllegalState {
	reThrowAsChecked(() -> _completeWithUnknownPayment(invoiceNumber, paidAmount,
		paidCurency, paidInstant, paidReference));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String processQazkomFailure(final String failureXml) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> _processQazkomFailure(failureXml));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void completeWithQazkomPayment(final String postbackXml) throws IllegalArgument, IllegalState {
	reThrowAsChecked(() -> _completeWithQazkomPayment(postbackXml));
    }

    // PRIVATE

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(EpaymentFacade.class) //
	    .build();

    @Inject
    private InvoiceDAO invoiceDAO;

    @Inject
    private PaymentDAO paymentDAO;

    @Inject
    private NotificationFacade notifications;

    @Resource(lookup = JNDI_CONFIG)
    private Properties epaymentConfig;

    @Inject
    private QazkomOrderDAO qoDAO;

    @Inject
    private QazkomPaymentDAO qpDAO;

    @Inject
    private QazkomErrorDAO qeDAO;

    private boolean _hasInvoiceWithNumber(final String invoiceNumber) {
	try {
	    _invoiceByNumber(invoiceNumber);
	    return true;
	} catch (InvoiceNotFound e) {
	    return false;
	}
    }

    private Invoice _invoiceByNumber(final String invoiceNumber) {
	MyStrings.requireNonEmpty(invoiceNumber, "invoiceNumber");
	return invoiceDAO.optionalByNumber(invoiceNumber) //
		.orElseThrow(MyExceptions.supplier(InvoiceNotFound::new, "Invoice not found with number %1$s",
			invoiceNumber));
    }

    private URI _getDefaultPaymentURI(String invoiceNumber) {
	final Invoice invoice = _invoiceByNumber(invoiceNumber);
	final String pattern = epaymentConfig.getProperty(PROPERTY_DEFAULT_PAYMENT_URI_PATTERN);
	try {
	    final String parsed = pattern //
		    .replace("@INVOICE_ID@", invoice.getNumber()) //
		    .replace("@INVOICE_NUMBER@", invoice.getNumber()) //
		    .replace("@LANG@", invoice.getConsumerPreferLanguage().getTag());
	    return new URI(parsed);
	} catch (final URISyntaxException e) {
	    throw new IllegalArgumentException(e);
	} catch (final NullPointerException e) {
	    throw new IllegalArgumentException(e);
	}
    }

    private String _invoiceAccept(final InvoiceBuilder builder) {
	final Invoice saved = invoiceDAO.save(builder.testingNumberWith(invoiceDAO::isUniqueNumber) //
		.build());
	if (saved.optionalConsumerEmail().isPresent()) {
	    saved.unlazy();
	    reThrowAsUnchecked(() ->
	    //
	    notifications.send(Notification.builder() //
		    .withChannel(NotificationChannel.EMAIL) //
		    .withEvent(NotificationEventType.PAYMENT_LINK) //
		    .withRecipient(NotificationRecipientType.REQUESTER) //
		    .withProperty("paymentUrl", _getDefaultPaymentURI(saved.getNumber()).toString()) //
		    .forEntity(saved) //
		    .build() //
	    )
	    //
	    );
	    logger.FINE.log("Payment accepted notification sent '%1$s'", saved);
	}
	return saved.getNumber();
    }

    private void _completeWithUnknownPayment(String invoiceNumber, Double paidAmount, Currency paidCurency,
	    Instant paidInstant, String paidReference) {
	final UnknownPayment up;
	{
	    final UnknownPayment temp = UnknownPayment.builder() //
		    .withAmount(paidAmount) //
		    .withCurrency(paidCurency) //
		    .withCreationInstant(MyOptionals.of(paidInstant)) //
		    .withReferenceNumber(MyOptionals.of(paidReference)) //
		    .build();
	    up = paymentDAO.save(temp);
	}
	final Invoice invoice = _invoiceByNumber(invoiceNumber);
	_invoiceHasPaidBy(invoice, up);
    }

    private void _completeWithQazkomPayment(final String postbackXml) {
	MyObjects.requireNonNull(qazkomSettings, "qazkomSettings");
	MyStrings.requireNonEmpty(postbackXml, "postbackXml");

	logger.INFO.log("New postback '%1$s'", postbackXml);

	try {

	    final QazkomPayment qp;
	    {
		final QazkomPayment temp = QazkomPayment.builder() //
			.fromRawXml(postbackXml) //
			.withBankCertificate(qazkomSettings.QAZKOM_BANK_CERTIFICATE) //
			.build();
		if (!qpDAO.isUniqueNumber(temp.getOrderNumber()))
		    throw MyExceptions.illegalStateFormat("Already processed QazkomPayment with order number %1$s",
			    temp.getOrderNumber());
		qp = qpDAO.save(temp);
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

	    _invoiceHasPaidBy(i, qpDAO.save(qp));

	} catch (IllegalArgumentException | IllegalStateException e) {
	    logger.WARN.log(e, "Error processing postback : %1$s", e.getMessage());
	    throw e;
	}
    }

    private PaymentMethod _qazkomHttpMethod(final URI postbackURI, final URI failureURI, final URI returnURI,
	    final Invoice forInvoice) {
	MyObjects.requireNonNull(postbackURI, "postbackURI");
	MyObjects.requireNonNull(failureURI, "failureURI");
	MyObjects.requireNonNull(returnURI, "returnURI");
	MyObjects.requireNonNull(forInvoice, "forInvoice");

	final QazkomOrder o = qoDAO.optionalLatestForInvoice(forInvoice) //
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

	final Http http = new Http(qazkomSettings.QAZKOM_EPAY_URI, qazkomSettings.QAZKOM_EPAY_HTTP_METHOD,
		MyMaps.of(
			"Signed_Order_B64", MyStrings.requireNonEmpty(o.getOrderDoc().getBase64Xml(), "content"), //
			"template", qazkomSettings.QAZKOM_EPAY_TEMPLATE, //
			"email", forInvoice.optionalConsumerEmail().orElse(""), //
			"PostLink", postbackURI.toASCIIString(),
			"FailurePostLink", failureURI.toASCIIString(),
			"Language", forInvoice.getConsumerPreferLanguage().getTag(), //
			"appendix", o.getCartDoc().getBase64Xml(), //
			"BackLink", returnURI.toString() //
		));

	return new PaymentMethod() {
	    private static final long serialVersionUID = 1L;

	    @Override
	    public Http getHttp() {
		return http;
	    }
	};
    }

    private String _processQazkomFailure(final String failureXml) {
	MyStrings.requireNonEmpty(failureXml, "failureXml");

	logger.INFO.log("New failure '%1$s'", failureXml);

	try {

	    final QazkomError qe;
	    {
		final QazkomError temp = QazkomError.builder() //
			.fromRawXml(failureXml) //
			.build();
		qe = qeDAO.save(temp);
	    }

	    final QazkomOrder qo = qoDAO.optionalByNumber(qe.getOrderNumber()) //
		    .orElseThrow(illegalArgumentSupplierFormat(
			    "No QazkomOrder found or reference is invlaid - '%1$s'", qe.getOrderNumber()));
	    logger.INFO.log("QazkomOrder OK - '%1$s'", qo);

	    qo.attachError(qe);
	    qoDAO.save(qo);
	    qeDAO.save(qe);

	    return qe.getMessage();
	} catch (IllegalArgumentException | IllegalStateException e) {
	    logger.WARN.log(e, "Error processing failure : %1$s", e.getMessage());
	    throw e;
	}
    }

    @Inject
    @JmsDestination(EpaymentDestinations.INVOICE_HAS_PAID)
    private JmsEventNotificatorClient<XmlInvoiceHasPaidEvent> invoiceHasPaidEventNotificatorClient;

    private void _invoiceHasPaidBy(final Invoice invoice, final Payment payment) {
	MyObjects.requireNonNull(invoice, "invoice");
	MyObjects.requireNonNull(payment, "payment");

	invoice.paidBy(payment);
	invoiceDAO.save(invoice);
	paymentDAO.save(payment);

	logger.INFO.log("Ivoice has paid successfuly '%1$s'", invoice);

	if (invoice.optionalConsumerEmail().isPresent()) {
	    invoice.unlazy();
	    reThrowAsUnchecked(() ->
	    //
	    notifications.send(Notification.builder() //
		    .withChannel(NotificationChannel.EMAIL) //
		    .withEvent(NotificationEventType.PAYMENT_SUCCESS) //
		    .withRecipient(NotificationRecipientType.REQUESTER) //
		    .forEntity(invoice) //
		    .build() //
	    )
	    //
	    );
	}

	{
	    final String methodName = payment.getMethod().regular();
	    final Instant paid = payment.getCreated();
	    final Double amount = payment.getAmount();
	    final Currency currency = payment.getCurrency();
	    final String ref = payment.getReferenceNumber();
	    final String invoiceNumber = invoice.getNumber();
	    final String externalId = invoice.getExternalId();

	    final XmlInvoiceHasPaidEvent ev = new XmlInvoiceHasPaidEvent();
	    ev.setAmount(amount);
	    ev.setCurrency(currency);
	    ev.setInstant(paid);
	    ev.setInvoiceNumber(invoiceNumber);
	    ev.setMethod(methodName);
	    ev.setReferenceNumber(ref);
	    ev.setExternalId(externalId);

	    invoiceHasPaidEventNotificatorClient.eventNotify(ev);
	}
    }
}
