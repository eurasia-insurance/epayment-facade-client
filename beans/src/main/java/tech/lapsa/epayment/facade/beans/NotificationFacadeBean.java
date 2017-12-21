package tech.lapsa.epayment.facade.beans;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.Destination;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.facade.NotificationFacade;
import tech.lapsa.epayment.facade.NotificationFacade.NotificationFacadeLocal;
import tech.lapsa.epayment.facade.NotificationFacade.NotificationFacadeRemote;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.javax.jms.client.JmsClientFactory;
import tech.lapsa.javax.jms.client.JmsEventNotificatorClient;

@Stateless(name = NotificationFacade.BEAN_NAME)
public class NotificationFacadeBean implements NotificationFacadeLocal, NotificationFacadeRemote {

    // READERS

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void send(final Notification notification) throws IllegalArgument {
	try {
	    _send(notification);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    // PRIVATE

    private void _send(final Notification notification) throws IllegalArgumentException {
	MyObjects.requireNonNull(notification, "notification");
	final Destination destination = resolveDestination(notification);
	final JmsEventNotificatorClient<Invoice> notificator = jmsFactory.createEventNotificator(destination);
	notificator.eventNotify(notification.getEntity(), notification.getProperties());
    }

    @Inject
    private JmsClientFactory jmsFactory;

    @Resource(name = EpaymentDestinations.NOTIFIER_PAYMENTLINK_REQUESTER_EMAIL)
    private Destination paymentLinkUserEmail;

    @Resource(name = EpaymentDestinations.NOTIFIER_PAYMENTSUCCESS_REQUESTER_EMAIL)
    private Destination paymentSucessUserEmail;

    private Destination resolveDestination(final Notification notification) throws IllegalArgumentException {
	MyObjects.requireNonNull(notification, "notification");
	out: switch (notification.getEvent()) {
	case PAYMENT_SUCCESS:
	    switch (notification.getChannel()) {
	    case EMAIL:
		switch (notification.getRecipientType()) {
		case REQUESTER:
		    return paymentSucessUserEmail;
		case COMPANY:
		    break out;
		}
		break out;
	    }
	    break out;
	case PAYMENT_LINK:
	    switch (notification.getChannel()) {
	    case EMAIL:
		switch (notification.getRecipientType()) {
		case REQUESTER:
		    return paymentLinkUserEmail;
		case COMPANY:
		    break out;
		}
		break out;
	    }
	    break out;
	}
	throw MyExceptions.format(IllegalArgumentException::new,
		"Can't resolve Destination for channel '%2$s' recipient '%3$s' stage '%1$s'",
		notification.getEvent(), // 1
		notification.getChannel(), // 2
		notification.getRecipientType() // 3
	);
    }
}
