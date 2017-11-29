package tech.lapsa.epayment.facade.beans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Currency;
import java.util.Properties;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import tech.lapsa.epayment.dao.InvoiceDAO;
import tech.lapsa.epayment.dao.PaymentDAO;
import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.domain.Payment;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.epayment.notifier.Notification;
import tech.lapsa.epayment.notifier.NotificationChannel;
import tech.lapsa.epayment.notifier.NotificationRecipientType;
import tech.lapsa.epayment.notifier.NotificationRequestStage;
import tech.lapsa.epayment.notifier.Notifier;
import tech.lapsa.epayment.shared.entity.XmlInvoiceHasPaidEvent;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.javax.jms.JmsClientFactory.JmsEventNotificator;
import tech.lapsa.javax.jms.JmsDestinationMappedName;
import tech.lapsa.javax.jms.JmsServiceEntityType;

@Stateless
public class EpaymentFacadeBean implements EpaymentFacade {

    @Inject
    private InvoiceDAO invoiceDAO;

    @Inject
    private PaymentDAO paymentDAO;

    @Inject
    private Notifier notifier;

    @Resource(lookup = Constants.JNDI_CONFIG)
    private Properties epaymentConfig;

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public URI getDefaultPaymentURI(final Invoice invoice) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {
	    MyObjects.requireNonNull(invoice, "invoice");
	    final String pattern = epaymentConfig.getProperty(Constants.PROPERTY_DEFAULT_PAYMENT_URI_PATTERN);
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
	});
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Invoice accept(final Invoice invoice) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {
	    final Invoice saved = invoiceDAO.save(invoice);
	    if (invoice.optionalConsumerEmail().isPresent()) {
		saved.unlazy();
		notifier.send(Notification.builder() //
			.withChannel(NotificationChannel.EMAIL) //
			.withEvent(NotificationRequestStage.PAYMENT_LINK) //
			.withRecipient(NotificationRecipientType.REQUESTER) //
			.withProperty("paymentUrl", getDefaultPaymentURI(saved).toString()) //
			.forEntity(saved) //
			.build() //
		);
		logger.FINE.log("Payment accepted notification sent '%1$s'", invoice);
	    }
	    return saved;
	});
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Invoice completeAndAccept(final InvoiceBuilder builder) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {
	    return accept(builder.testingNumberWith(invoiceDAO::isUniqueNumber) //
		    .build());
	});
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Invoice invoiceByNumber(final String number) throws IllegalArgument, IllegalState, InvoiceNotFound {
	return reThrowAsChecked(() -> {
	    MyStrings.requireNonEmpty(number, "number");
	    return invoiceDAO.optionalByNumber(MyStrings.requireNonEmpty(number, "number")) //
		    .orElseThrow(() -> new InvoiceNotFound());
	});
    }

    private MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(EpaymentFacade.class) //
	    .build();

    @Inject
    @JmsDestinationMappedName(EpaymentDestinations.INVOICE_HAS_PAID)
    @JmsServiceEntityType(XmlInvoiceHasPaidEvent.class)
    private JmsEventNotificator<XmlInvoiceHasPaidEvent> invoiceHasPaidEventNotificator;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void invoiceHasPaidBy(final Invoice invoice, final Payment payment) throws IllegalArgument, IllegalState {
	reThrowAsChecked(() -> {

	    MyObjects.requireNonNull(invoice, "invoice");
	    MyObjects.requireNonNull(payment, "payment");

	    invoice.paidBy(payment);
	    invoiceDAO.save(invoice);
	    paymentDAO.save(payment);

	    logger.INFO.log("Ivoice has paid successfuly '%1$s'", invoice);

	    if (invoice.optionalConsumerEmail().isPresent()) {
		invoice.unlazy();
		notifier.send(Notification.builder() //
			.withChannel(NotificationChannel.EMAIL) //
			.withEvent(NotificationRequestStage.PAYMENT_SUCCESS) //
			.withRecipient(NotificationRecipientType.REQUESTER) //
			.forEntity(invoice) //
			.build() //
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

		XmlInvoiceHasPaidEvent ev = new XmlInvoiceHasPaidEvent();
		ev.setAmount(amount);
		ev.setCurrency(currency);
		ev.setInstant(paid);
		ev.setInvoiceNumber(invoiceNumber);
		ev.setMethod(methodName);
		ev.setReferenceNumber(ref);
		ev.setExternalId(externalId);

		invoiceHasPaidEventNotificator.eventNotify(ev);
	    }

	});

    }

}
