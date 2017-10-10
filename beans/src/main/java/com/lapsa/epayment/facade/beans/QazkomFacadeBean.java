package com.lapsa.epayment.facade.beans;

import java.time.Instant;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.lapsa.commons.function.MyObjects;
import com.lapsa.commons.function.MyStrings;
import com.lapsa.epayment.facade.Ebill;
import com.lapsa.epayment.facade.EpaymentFacade;
import com.lapsa.epayment.facade.QEpaymentSuccess;
import com.lapsa.epayment.facade.QazkomFacade;
import com.lapsa.epayment.facade.Response;
import com.lapsa.epayment.facade.ResponseBuilder;
import com.lapsa.kkb.core.KKBOrder;
import com.lapsa.kkb.core.KKBPaymentRequestDocument;
import com.lapsa.kkb.core.KKBPaymentResponseDocument;
import com.lapsa.kkb.core.KKBPaymentStatus;
import com.lapsa.kkb.dao.KKBEntityNotFound;
import com.lapsa.kkb.dao.KKBOrderDAO;
import com.lapsa.kkb.mesenger.KKBNotificationChannel;
import com.lapsa.kkb.mesenger.KKBNotificationRecipientType;
import com.lapsa.kkb.mesenger.KKBNotificationRequestStage;
import com.lapsa.kkb.mesenger.KKBNotifier;
import com.lapsa.kkb.services.KKBFormatException;
import com.lapsa.kkb.services.KKBResponseService;
import com.lapsa.kkb.services.KKBServiceError;
import com.lapsa.kkb.services.KKBValidationErrorException;
import com.lapsa.kkb.services.KKBWrongSignature;

@Stateless
@LocalBean
public class QazkomFacadeBean implements QazkomFacade {

    @EJB
    private KKBResponseService responseService;

    @EJB
    private KKBOrderDAO orderDAO;

    @EJB
    private KKBNotifier notifier;

    @EJB
    private EpaymentFacade facade;

    @Inject
    @QEpaymentSuccess
    private Event<Ebill> ebillPaidSuccessfuly;

    @Override
    public ResponseBuilder newResponseBuilder() {
	return new ResponseBuilderImpl();
    }

    public final class ResponseBuilderImpl implements ResponseBuilder {

	private String responseXml;

	private ResponseBuilderImpl() {
	}

	@Override
	public ResponseBuilder withXml(String responseXml) {
	    this.responseXml = responseXml;
	    return this;
	}

	@Override
	public Response build() {

	    KKBPaymentResponseDocument response = new KKBPaymentResponseDocument();
	    response.setCreated(Instant.now());
	    response.setContent(MyStrings.requireNonEmpty(responseXml, "ResponseImpl is empty"));

	    // verify format
	    try {
		responseService.validateResponseXmlFormat(response);
	    } catch (KKBFormatException e) {
		throw new IllegalArgumentException("Wrong xml format", e);
	    }

	    // validate signature
	    try {
		responseService.validateSignature(response, true);
	    } catch (KKBServiceError e) {
		throw new RuntimeException("Internal error", e);
	    } catch (KKBWrongSignature e) {
		throw new IllegalArgumentException("Wrong signature", e);
	    }

	    // find order by id
	    KKBOrder order = null;
	    try {
		String orderId = responseService.parseOrderId(response, true);
		order = orderDAO.findByIdByPassCache(orderId);
	    } catch (KKBEntityNotFound e) {
		throw new IllegalArgumentException("No payment order found or reference is invlaid", e);
	    }

	    // validate response to request
	    KKBPaymentRequestDocument request = order.getLastRequest();
	    if (request == null)
		throw new RuntimeException("There is no request for response found"); // fatal

	    try {
		responseService.validateResponse(order, true);
	    } catch (KKBValidationErrorException e) {
		throw new IllegalArgumentException("Responce validation failed", e);
	    }

	    return new ResponseImpl(response, order);
	}

	public final class ResponseImpl implements Response {
	    private final KKBPaymentResponseDocument response;
	    private final KKBOrder order;

	    private KKBOrder handled;

	    private ResponseImpl(final KKBPaymentResponseDocument response, final KKBOrder order) {
		this.order = MyObjects.requireNonNull(order, "order");
		this.response = MyObjects.requireNonNull(response, "response");
	    }

	    @Override
	    public Ebill handle() {
		if (handled != null)
		    throw new IllegalStateException("Already handled");

		// attach response
		order.setLastResponse(response);

		// set order status
		order.setStatus(KKBPaymentStatus.AUTHORIZATION_PASS);

		// paid instant
		Instant paymentInstant = responseService.parsePaymentTimestamp(response, true);
		order.setPaid(paymentInstant);

		// paid reference
		String paymentReference = responseService.parsePaymentReferences(response, true);
		order.setPaymentReference(paymentReference);

		handled = orderDAO.save(order);

		notifier.assignOrderNotification(KKBNotificationChannel.EMAIL, //
			KKBNotificationRecipientType.REQUESTER, //
			KKBNotificationRequestStage.PAYMENT_SUCCESS, //
			handled);

		Ebill ebill = facade.newEbillBuilder() //
			.withFetched(order.getId())
			.build();
		ebillPaidSuccessfuly.fire(ebill);
		return ebill;

	    }
	}
    }
}
