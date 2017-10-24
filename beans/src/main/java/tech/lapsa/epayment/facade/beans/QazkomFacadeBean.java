package tech.lapsa.epayment.facade.beans;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.lapsa.international.localization.LocalizationLanguage;
import com.lapsa.kkb.core.KKBOrder;
import com.lapsa.kkb.core.KKBPaymentRequestDocument;
import com.lapsa.kkb.core.KKBPaymentResponseDocument;
import com.lapsa.kkb.core.KKBPaymentStatus;
import com.lapsa.kkb.services.KKBEpayConfigurationService;
import com.lapsa.kkb.services.KKBFormatException;
import com.lapsa.kkb.services.KKBResponseService;
import com.lapsa.kkb.services.KKBServiceError;
import com.lapsa.kkb.services.KKBValidationErrorException;
import com.lapsa.kkb.services.KKBWrongSignature;

import tech.lapsa.epayment.dao.KKBOrderDAO;
import tech.lapsa.epayment.facade.Ebill;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.QazkomFacade;
import tech.lapsa.epayment.facade.QazkomFacade.PaymentMethodBuilder.PaymentMethod.HttpMethod;
import tech.lapsa.java.commons.function.MyMaps;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;

@Stateless
public class QazkomFacadeBean implements QazkomFacade {

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(QazkomFacade.class) //
	    .addWithPrefix("QAZKOM") //
	    .addWithCAPS() //
	    .build();

    @Inject
    private KKBEpayConfigurationService epayConfig;

    @Inject
    private KKBResponseService responseService;

    @Inject
    private KKBOrderDAO orderDAO;

    @Inject
    private EpaymentFacade facade;

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
	    this.responseXml = MyStrings.requireNonEmpty(responseXml, "responseXml");
	    return this;
	}

	@Override
	public ResponseHandler build() {

	    logger.FINE.log("RESPONSE RECEIVED '%1$s'", responseXml);

	    KKBPaymentResponseDocument response = new KKBPaymentResponseDocument();
	    response.setCreated(Instant.now());
	    response.setContent(MyStrings.requireNonEmpty(responseXml, "responseXml"));

	    // verify format
	    try {
		responseService.validateResponseXmlFormat(response);
		logger.FINE.log("XML FORMAT IS VALID");
	    } catch (KKBFormatException e) {
		logger.WARNING.log("WRONG XML FORMAT");
		throw new IllegalArgumentException("Wrong xml format", e);
	    }

	    // validate signature
	    try {
		responseService.validateSignatureNoFormatCheck(response);
		logger.FINE.log("SIGNATURE IS VALID");
	    } catch (KKBServiceError e) {
		throw new RuntimeException("Internal error", e);
	    } catch (KKBWrongSignature e) {
		logger.WARNING.log("WRONG SIGNATURE");
		throw new IllegalArgumentException("Wrong signature", e);
	    }

	    // find order by id
	    String orderId = responseService.parseOrderIdNoFormatCheck(response);
	    KKBOrder order = orderDAO.optionalByIdByPassCache(orderId)
		    .orElseThrow(() -> new IllegalArgumentException("No payment order found or reference is invlaid"));
	    logger.FINE.log("INVOICE FOUND WITH ID '%1$s'", orderId);

	    // validate response to request
	    KKBPaymentRequestDocument request = order.getLastRequest();
	    if (request == null) {
		logger.WARNING.log("LAST REQUEST IS NOT FOUND");
		throw new RuntimeException("There is no request for response found"); // fatal
	    }

	    try {
		responseService.validateResponseWithRequestNoFormatCheck(request, response);
		logger.FINE.log("RESPONSE-WITH-REQUEST VALIDATION SUCCESSFUL");
	    } catch (KKBValidationErrorException e) {
		logger.WARNING.log("RESPONSE-WITH-REQUEST VALIDATION FAILED");
		throw new IllegalArgumentException("Responce validation failed", e);
	    }

	    logger.FINE.log("ResponseHandler BUILD SUCCESSFUL");
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

		logger.FINE.log("Start handling");

		// attach response
		order.setStatus(KKBPaymentStatus.AUTHORIZATION_PASS);
		order.setLastResponse(response);

		KKBOrder saved = orderDAO.save(order);
		logger.FINE.log("LAST RESPONSE SAVED");

		// paid instant
		Instant paymentInstant = responseService.parsePaymentTimestampNoFormatCheck(response);

		// paid reference
		String paymentReference = responseService.parsePaymentReferencesNoFormatCheck(response);

		Ebill ebill = facade.newEbillPaidMarkerBuilder() //
			.usingId(saved.getId()) //
			.with(paymentInstant, paymentReference) //
			.build() //
			.mark();

		handled = true;

		logger.FINE.log("HANDLED SUCCESSFULY invoice id = '%1$s'", ebill.getId());

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
	    KKBOrder order = orderDAO.optionalById(ebill.getId())
		    .orElseThrow(() -> new IllegalArgumentException("Invalid ebill"));
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
