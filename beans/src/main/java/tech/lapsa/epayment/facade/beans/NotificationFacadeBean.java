package tech.lapsa.epayment.facade.beans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.Destination;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.facade.NotificationFacade;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.javax.jms.client.JmsClientFactory;
import tech.lapsa.javax.jms.client.JmsEventNotificatorClient;

@Stateless
public class NotificationFacadeBean implements NotificationFacade {

    // READERS

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void send(final Notification notification) throws IllegalArgument, IllegalState {
	reThrowAsChecked(() -> _send(notification));
    }

    // PRIVATE

    private void _send(final Notification notification) {
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

    private Destination resolveDestination(final Notification notification) {
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
	throw new IllegalStateException(String.format(
		"Can't resolve Destination for channel '%2$s' recipient '%3$s' stage '%1$s'",
		notification.getEvent(), // 1
		notification.getChannel(), // 2
		notification.getRecipientType() // 3
	));
    }
}
