package com.lapsa.kkb.facade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.lapsa.commons.function.MyCollections;
import com.lapsa.commons.function.MyNumbers;
import com.lapsa.commons.function.MyStrings;
import com.lapsa.fin.FinCurrency;
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
import com.lapsa.kkb.services.KKBDocumentComposerService;
import com.lapsa.kkb.services.KKBFactory;
import com.lapsa.kkb.services.KKBFormatException;
import com.lapsa.kkb.services.KKBResponseService;
import com.lapsa.kkb.services.KKBServiceError;
import com.lapsa.kkb.services.KKBValidationErrorException;
import com.lapsa.kkb.services.KKBWrongSignature;

@ApplicationScoped
public class QazkomFacade {

    @Inject
    private KKBDocumentComposerService composer;

    @Inject
    private KKBResponseService responseService;

    @Inject
    private KKBFactory factory;

    @Inject
    private KKBOrderDAO orderDAO;

    @Inject
    private KKBNotifier notifier;

    public PaymentBuilder newPaymentBuilder() {
	return new PaymentBuilder();
    }

    public ResponseBuilder newResponseBuilder() {
	return new ResponseBuilder();
    }

    public final class ResponseBuilder {

	private String responseXml;

	private ResponseBuilder() {
	}

	public ResponseBuilder withXml(String responseXml) {
	    this.responseXml = responseXml;
	    return this;
	}

	public Response build() {

	    KKBPaymentResponseDocument response = new KKBPaymentResponseDocument();
	    response.setCreated(Instant.now());
	    response.setContent(MyStrings.requireNonEmpty(responseXml, "Response is empty"));

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

	    return new Response(response, order);
	}

	public final class Response {
	    private final KKBPaymentResponseDocument response;
	    private final KKBOrder order;

	    private KKBOrder handled;

	    private Response(final KKBPaymentResponseDocument response, final KKBOrder order) {
		this.order = Objects.requireNonNull(order);
		this.response = Objects.requireNonNull(response);
	    }

	    public HandleResult handle() {
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

		return new HandleResult(handled.getExternalId(), paymentReference, paymentInstant);

	    }

	    public final class HandleResult {

		private final String externalId;
		private final Instant instant;
		private final String reference;

		private HandleResult(final String externalId, final String paymentReference,
			final Instant paidInstant) {
		    this.externalId = MyStrings.requireNonEmpty(externalId);
		    this.reference = MyStrings.requireNonEmpty(paymentReference);
		    this.instant = Objects.requireNonNull(paidInstant);
		}

		public Instant getInstant() {
		    return instant;
		}

		public String getReference() {
		    return reference;
		}

		public String getExternalId() {
		    return externalId;
		}
	    }
	}
    }

    //

    public final class PaymentBuilder {
	private List<BuilderItem> items = new ArrayList<>();
	private String orderId;
	private String email;
	private LocalizationLanguage language;
	private String name;
	private String externalId;
	private FinCurrency currency;

	private PaymentBuilder() {
	}

	public PaymentBuilder withMoreItem(String productName, double cost, int quantity) {
	    items.add(new BuilderItem(productName, cost, quantity));
	    return this;
	}

	public PaymentBuilder winthGeneratedId() {
	    this.orderId = factory.generateNewOrderId();
	    return this;
	}

	public PaymentBuilder withOrderCurrencty(FinCurrency currency) {
	    this.currency = currency;
	    return this;
	}

	public PaymentBuilder withDefaultCurrency() {
	    this.currency = FinCurrency.KZT;
	    return this;
	}

	public PaymentBuilder withId(String orderId) {
	    this.orderId = orderId;
	    return this;
	}

	public PaymentBuilder withConsumer(String email, LocalizationLanguage language, String name) {
	    withConsumerLanguage(language);
	    withConsumerEmail(email);
	    withConsumerName(name);
	    return this;
	}

	public PaymentBuilder withConsumer(String email, LocalizationLanguage language) {
	    withConsumerLanguage(language);
	    withConsumerEmail(email);
	    return this;
	}

	public PaymentBuilder withConsumerName(String name) {
	    this.name = MyStrings.requireNonEmpty(name, "name");
	    return this;
	}

	public PaymentBuilder withConsumerEmail(String email) {
	    this.email = MyStrings.requireNonEmpty(email, "email");
	    return this;
	}

	public PaymentBuilder withExternalId(String externalId) {
	    this.externalId = MyStrings.requireNonEmpty(externalId);
	    return this;
	}

	public PaymentBuilder withExternalId(Integer externalId) {
	    this.externalId = MyNumbers.requireNonZero(externalId).toString();
	    return this;
	}

	public PaymentBuilder withConsumerLanguage(LocalizationLanguage language) {
	    this.language = Objects.requireNonNull(language, "language");
	    return this;
	}

	private final class BuilderItem {
	    private final String product;
	    private final double cost;
	    private final int quantity;

	    private BuilderItem(String product, double cost, int quantity) {
		this.product = MyStrings.requireNonEmpty(product, "product");
		this.cost = MyNumbers.requireNonZero(cost, "cost");
		this.quantity = MyNumbers.requireNonZero(quantity, "quantity");
	    }
	}

	public Payment build() {
	    KKBOrder o = new KKBOrder();
	    o.setId(MyStrings.requireNonEmpty(orderId, "orderId"));
	    o.setCreated(Instant.now());
	    o.setStatus(KKBPaymentStatus.NEW);
	    o.setCurrency(Objects.requireNonNull(currency, "currency"));
	    o.setConsumerEmail(MyStrings.requireNonEmpty(email, "email"));
	    o.setConsumerLanguage(Objects.requireNonNull(language, "language"));
	    o.setConsumerName(MyStrings.requireNonEmpty(name, "name"));
	    o.setExternalId(externalId);

	    MyCollections.requireNonEmpty(items, "items is empty") //
		    .stream() //
		    .forEach(x -> factory.generateNewOrderItem(x.product, x.cost, x.quantity, o));

	    return new Payment(o);
	}

	public final class Payment {

	    private final KKBOrder order;
	    private KKBOrder accepted;

	    private Payment(KKBOrder order) {
		this.order = Objects.requireNonNull(order);
	    }

	    public AcceptResult accept() {
		if (accepted != null)
		    throw new IllegalStateException("Already acceted");

		composer.composeCart(order);
		composer.composeRequest(order);

		accepted = orderDAO.save(order);

		notifier.assignOrderNotification(KKBNotificationChannel.EMAIL, //
			KKBNotificationRecipientType.REQUESTER, //
			KKBNotificationRequestStage.PAYMENT_LINK, //
			accepted);

		return new AcceptResult(accepted.getId());
	    }

	    public final class AcceptResult {
		private final String reference;

		private AcceptResult(final String reference) {
		    this.reference = MyStrings.requireNonEmpty(reference);
		}

		public String getReference() {
		    return reference;
		}
	    }

	}
    }
}
