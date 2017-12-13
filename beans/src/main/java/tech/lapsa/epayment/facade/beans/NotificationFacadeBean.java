package tech.lapsa.epayment.facade.beans;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.Destination;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.facade.NotificationFacade.NotificationFacadeLocal;
import tech.lapsa.epayment.facade.NotificationFacade.NotificationFacadeRemote;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.javax.jms.client.JmsClientFactory;
import tech.lapsa.javax.jms.client.JmsEventNotificatorClient;

@Stateless
public class NotificationFacadeBean implements NotificationFacadeLocal, NotificationFacadeRemote {

    // READERS

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void send(final Notification notification) throws IllegalArgumentException {
	_send(notification);
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
	switch (notification.getEvent()) {
	case PAYMENT_SUCCESS:
	    switch (notification.getChannel()) {
	    case EMAIL:
		switch (notification.getRecipientType()) {
		case REQUESTER:
		    return paymentSucessUserEmail;
		default:
		}
	    default:
	    }
	case PAYMENT_LINK:
	    switch (notification.getChannel()) {
	    case EMAIL:
		switch (notification.getRecipientType()) {
		case REQUESTER:
		    return paymentLinkUserEmail;
		default:
		}
	    default:
	    }
	}
	throw MyExceptions.illegalArgumentFormat(
		"Can't resolve Destination for channel '%2$s' recipient '%3$s' stage '%1$s'",
		notification.getEvent(), // 1
		notification.getChannel(), // 2
		notification.getRecipientType() // 3
	);
    }
}
