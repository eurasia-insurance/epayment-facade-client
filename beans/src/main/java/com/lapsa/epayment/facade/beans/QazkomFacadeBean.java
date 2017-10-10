package com.lapsa.epayment.facade.beans;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.lapsa.commons.function.MyMaps;
import com.lapsa.commons.function.MyObjects;
import com.lapsa.commons.function.MyStrings;
import com.lapsa.epayment.facade.Ebill;
import com.lapsa.epayment.facade.EpaymentFacade;
import com.lapsa.epayment.facade.QEpaymentSuccess;
import com.lapsa.epayment.facade.QazkomFacade;
import com.lapsa.epayment.facade.QazkomFacade.PaymentMethodBuilder.PaymentMethod.HttpMethod;
import com.lapsa.international.localization.LocalizationLanguage;
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
import com.lapsa.kkb.services.KKBEpayConfigurationService;
import com.lapsa.kkb.services.KKBFormatException;
import com.lapsa.kkb.services.KKBResponseService;
import com.lapsa.kkb.services.KKBServiceError;
import com.lapsa.kkb.services.KKBValidationErrorException;
import com.lapsa.kkb.services.KKBWrongSignature;

@Stateless
public class QazkomFacadeBean implements QazkomFacade {

    @Inject
    private KKBEpayConfigurationService epayConfig;

    @Inject
    private KKBResponseService responseService;

    @Inject
    private KKBOrderDAO orderDAO;

    @Inject
    private KKBNotifier notifier;

    @Inject
    private EpaymentFacade facade;

    @Inject
    @QEpaymentSuccess
    private Event<Ebill> ebillPaidSuccessfuly;

    @Override
    public ResponseHandlerBuilder newResponseHandlerBuilder() {
	return new QazkomResponseHandlerBuilder();
    }

    final class QazkomResponseHandlerBuilder implements ResponseHandlerBuilder {

	private String responseXml;

	private QazkomResponseHandlerBuilder() {
	}

	@Override
	public ResponseHandlerBuilder withXml(String responseXml) {
	    this.responseXml = responseXml;
	    return this;
	}

	@Override
	public ResponseHandler build() {

	    KKBPaymentResponseDocument response = new KKBPaymentResponseDocument();
	    response.setCreated(Instant.now());
	    response.setContent(MyStrings.requireNonEmpty(responseXml, "QazkomResponseHandler is empty"));

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

	    return new QazkomResponseHandler(response, order);
	}

	private final class QazkomResponseHandler implements ResponseHandler {

	    private boolean handled = false;

	    private final KKBPaymentResponseDocument response;
	    private final KKBOrder order;

	    private QazkomResponseHandler(final KKBPaymentResponseDocument response, final KKBOrder order) {
		this.order = MyObjects.requireNonNull(order, "order");
		this.response = MyObjects.requireNonNull(response, "response");
	    }

	    @Override
	    public Ebill handle() {
		if (handled)
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

		KKBOrder saved = orderDAO.save(order);

		notifier.assignOrderNotification(KKBNotificationChannel.EMAIL, //
			KKBNotificationRecipientType.REQUESTER, //
			KKBNotificationRequestStage.PAYMENT_SUCCESS, //
			saved);

		Ebill ebill = facade.newEbillFetcherBuilder() //
			.usingId(saved.getId()) //
			.build() //
			.fetch();
		ebillPaidSuccessfuly.fire(ebill);

		handled = true;

		return ebill;

	    }
	}
    }

    @Override
    public PaymentMethodBuilder newPaymentMethodBuilder() {
	return new QazkomPaymentMethodBuilder();
    }

    final class QazkomPaymentMethodBuilder implements PaymentMethodBuilder {

	private final String template = epayConfig.getTemplateName();
	private final URI epayAddress = epayConfig.getEpayURI();
	private final String epayMethod = "POST";

	private URI postbackURI;
	private URI returnUri;

	private String content;
	private String requestAppendix;
	private String consumerEmail;
	private LocalizationLanguage consumerLanguage;

	@Override
	public PaymentMethodBuilder withPostbackURI(URI postbackURL) {
	    this.postbackURI = MyObjects.requireNonNull(postbackURL, "postbackURI");
	    return this;
	}

	@Override
	public PaymentMethodBuilder withReturnURI(URI returnUri) {
	    this.returnUri = MyObjects.requireNonNull(returnUri, "returnUri");
	    return this;
	}

	@Override
	public PaymentMethodBuilder forEbill(Ebill ebill) {
	    // сдесь по идее надо собрать новый документ, подписать его и
	    // подготовить для HTTP формы а не загружать из БД
	    KKBOrder order = null;
	    try {
		order = orderDAO.findById(ebill.getId());
	    } catch (KKBEntityNotFound e) {
		throw new IllegalArgumentException("Invalid ebill");
	    }
	    this.content = order.getLastRequest().getContentBase64();
	    this.requestAppendix = order.getLastCart().getContentBase64();
	    this.consumerEmail = order.getConsumerEmail();
	    this.consumerLanguage = order.getConsumerLanguage();
	    return this;
	}

	@Override
	public PaymentMethodBuilder withConsumerLanguage(LocalizationLanguage language) {
	    this.consumerLanguage = MyObjects.requireNonNull(language, "language");
	    return this;
	}

	@Override
	public PaymentMethod build() {
	    HttpMethod http = new QazkomHttpMethod(epayAddress, epayMethod, MyMaps.of(
		    "Signed_Order_B64", MyObjects.requireNonNull(content, "content"), //
		    "template", MyStrings.requireNonEmpty(template, "template"), //
		    "email", MyStrings.requireNonEmpty(consumerEmail, "consumerEmail"), //
		    "PostLink", MyObjects.requireNonNull(postbackURI, "postbackURI").toString(),
		    "Language", MyObjects.requireNonNull(consumerLanguage, "consumerLanguage").getTag(), //
		    "appendix", MyStrings.requireNonEmpty(requestAppendix, "requestAppendix"), //
		    "BackLink", MyObjects.requireNonNull(returnUri, "returnUri").toString() //
	    ));
	    return new QazkomPaymentMethod(http);
	}

	final class QazkomHttpMethod implements HttpMethod {

	    final URI httpAddress;
	    final String httpMethod;
	    final Map<String, String> httpParams;

	    private QazkomHttpMethod(URI httpAddress, String httpMethod, Map<String, String> httpParams) {
		this.httpAddress = MyObjects.requireNonNull(httpAddress, "httpAddress");
		this.httpMethod = MyStrings.requireNonEmpty(httpMethod, "httpMethod");
		this.httpParams = Collections.unmodifiableMap(MyObjects.requireNonNull(httpParams, "httpParams"));
	    }

	    @Override
	    public URI getHttpAddress() {
		return httpAddress;
	    }

	    @Override
	    public String getHttpMethod() {
		return httpMethod;
	    }

	    @Override
	    public Map<String, String> getHttpParams() {
		return httpParams;
	    }

	}

	final class QazkomPaymentMethod implements PaymentMethod {

	    final HttpMethod http;

	    private QazkomPaymentMethod(final HttpMethod http) {
		this.http = MyObjects.requireNonNull(http, "http");
	    }

	    @Override
	    public HttpMethod getHttp() {
		return http;
	    }

	}
    }
}
