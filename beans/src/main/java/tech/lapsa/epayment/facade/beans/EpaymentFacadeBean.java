package tech.lapsa.epayment.facade.beans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.annotation.Resource;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import com.lapsa.fin.FinCurrency;

import tech.lapsa.epayment.dao.InvoiceDAO;
import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.epayment.notifier.NotificationChannel;
import tech.lapsa.epayment.notifier.NotificationRecipientType;
import tech.lapsa.epayment.notifier.NotificationRequestStage;
import tech.lapsa.epayment.notifier.Notifier;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

@Stateless
public class EpaymentFacadeBean implements EpaymentFacade {

    @Inject
    private InvoiceDAO dao;

    @Inject
    private Notifier notifier;

    @Resource(lookup = Constants.JNDI_CONFIG)
    private Properties epaymentConfig;

    @Override
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
    public Invoice accept(final Invoice invoice) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {
	    final Invoice saved = dao.save(invoice);
	    saved.unlazy();
	    notifier.newNotificationBuilder() //
		    .withChannel(NotificationChannel.EMAIL) //
		    .withEvent(NotificationRequestStage.PAYMENT_LINK) //
		    .withRecipient(NotificationRecipientType.REQUESTER) //
		    .withProperty("paymentUrl", getDefaultPaymentURI(saved).toString()) //
		    .forEntity(saved) //
		    .build() //
		    .send();
	    return saved;
	});
    }

    @Override
    public Invoice completeAndAccept(final InvoiceBuilder builder) throws IllegalArgument, IllegalState {
	return reThrowAsChecked(() -> {
	    return accept(builder.testingNumberWith(dao::isUniqueNumber) //
		    .withCurrency(FinCurrency.KZT) //
		    .build());
	});
    }

    @Override
    public Invoice forNumber(final String number) throws IllegalArgument, IllegalState, InvoiceNotFound {
	return reThrowAsChecked(() -> {
	    MyStrings.requireNonEmpty(number, "number");
	    return dao.optionalByNumber(MyStrings.requireNonEmpty(number, "number")) //
		    .orElseThrow(() -> new InvoiceNotFound());
	});
    }

    private static final String JNDI_JMS_CONNECTION_FACTORY = "epayment/jms/connectionFactory";

    @Resource(name = JNDI_JMS_CONNECTION_FACTORY)
    private ConnectionFactory connectionFactory;

    public static final String JNDI_JMS_DEST_PAID_EBILLs = "epayment/jms/paidEbills";

    @Resource(name = JNDI_JMS_DEST_PAID_EBILLs)
    private Destination paidEbillsDestination;

    @Override
    public void completeAfterPayment(final Invoice invoice) throws IllegalArgument, IllegalState {
	reThrowAsChecked(() -> {
	    MyObjects.requireNonNull(invoice, "invoice");

	    invoice.unlazy();

	    notifier.newNotificationBuilder() //
		    .withChannel(NotificationChannel.EMAIL) //
		    .withEvent(NotificationRequestStage.PAYMENT_SUCCESS) //
		    .withRecipient(NotificationRecipientType.REQUESTER) //
		    .forEntity(invoice) //
		    .build() //
		    .send();

	    try (Connection connection = connectionFactory.createConnection()) {
		final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		final MessageProducer producer = session.createProducer(paidEbillsDestination);
		final Message msg = session.createObjectMessage(invoice);
		producer.send(msg);
	    } catch (final JMSException e) {
		throw new EJBException("Failed to send invoice payment info", e);
	    }
	});
    }
}
