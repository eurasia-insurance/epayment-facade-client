package com.lapsa.epayment.facade.beans;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;

import com.lapsa.commons.function.MyCollections;
import com.lapsa.commons.function.MyMaps;
import com.lapsa.commons.function.MyNumbers;
import com.lapsa.commons.function.MyObjects;
import com.lapsa.commons.function.MyStrings;
import com.lapsa.epayment.facade.Ebill;
import com.lapsa.epayment.facade.Ebill.EbillStatus;
import com.lapsa.epayment.facade.Ebill.HttpFormTemplate;
import com.lapsa.epayment.facade.EbillItem;
import com.lapsa.epayment.facade.EpaymentFacade;
import com.lapsa.fin.FinCurrency;
import com.lapsa.international.localization.LocalizationLanguage;
import com.lapsa.kkb.core.KKBOrder;
import com.lapsa.kkb.core.KKBPaymentStatus;
import com.lapsa.kkb.dao.KKBEntityNotFound;
import com.lapsa.kkb.dao.KKBOrderDAO;
import com.lapsa.kkb.mesenger.KKBNotificationChannel;
import com.lapsa.kkb.mesenger.KKBNotificationRecipientType;
import com.lapsa.kkb.mesenger.KKBNotificationRequestStage;
import com.lapsa.kkb.mesenger.KKBNotifier;
import com.lapsa.kkb.services.KKBDocumentComposerService;
import com.lapsa.kkb.services.KKBEpayConfigurationService;
import com.lapsa.kkb.services.KKBFactory;

@Stateless
@LocalBean
public class EpaymentFacadeBean implements EpaymentFacade {

    @EJB
    private KKBDocumentComposerService composer;

    @EJB
    private KKBEpayConfigurationService epayConfig;

    @EJB
    private KKBFactory factory;

    @EJB
    private KKBOrderDAO orderDAO;

    @EJB
    private KKBNotifier notifier;

    @Override
    public PaymentBuilder newPaymentBuilder() {
	return new PaymentBuilderImpl();
    }

    public final class PaymentBuilderImpl implements PaymentBuilder {
	private List<BuilderItem> items = new ArrayList<>();
	private String orderId;
	private String email;
	private LocalizationLanguage language;
	private String name;
	private String externalId;
	private FinCurrency currency;

	private PaymentBuilderImpl() {
	}

	@Override
	public PaymentBuilder withMoreItem(String productName, double cost, int quantity) {
	    items.add(new BuilderItem(productName, cost, quantity));
	    return this;
	}

	@Override
	public PaymentBuilder winthGeneratedId() {
	    this.orderId = factory.generateNewOrderId();
	    return this;
	}

	@Override
	public PaymentBuilder withOrderCurrencty(FinCurrency currency) {
	    this.currency = currency;
	    return this;
	}

	@Override
	public PaymentBuilder withDefaultCurrency() {
	    this.currency = FinCurrency.KZT;
	    return this;
	}

	@Override
	public PaymentBuilder withId(String orderId) {
	    this.orderId = orderId;
	    return this;
	}

	@Override
	public PaymentBuilder withConsumer(String email, LocalizationLanguage language, String name) {
	    withConsumerLanguage(language);
	    withConsumerEmail(email);
	    withConsumerName(name);
	    return this;
	}

	@Override
	public PaymentBuilder withConsumer(String email, LocalizationLanguage language) {
	    withConsumerLanguage(language);
	    withConsumerEmail(email);
	    return this;
	}

	@Override
	public PaymentBuilder withConsumerName(String name) {
	    this.name = MyStrings.requireNonEmpty(name, "name");
	    return this;
	}

	@Override
	public PaymentBuilder withConsumerEmail(String email) {
	    this.email = MyStrings.requireNonEmpty(email, "email");
	    return this;
	}

	@Override
	public PaymentBuilder withExternalId(String externalId) {
	    this.externalId = MyStrings.requireNonEmpty(externalId, "externalId");
	    return this;
	}

	@Override
	public PaymentBuilder withExternalId(Integer externalId) {
	    this.externalId = MyNumbers.requireNonZero(externalId, "externalId").toString();
	    return this;
	}

	@Override
	public PaymentBuilder withConsumerLanguage(LocalizationLanguage language) {
	    this.language = MyObjects.requireNonNull(language, "language");
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

	@Override
	public Payment build() {
	    KKBOrder o = new KKBOrder(MyStrings.requireNonEmpty(orderId, "orderId"));
	    o.setCreated(Instant.now());
	    o.setStatus(KKBPaymentStatus.NEW);
	    o.setCurrency(MyObjects.requireNonNull(currency, "currency"));
	    o.setConsumerEmail(MyStrings.requireNonEmpty(email, "email"));
	    o.setConsumerLanguage(MyObjects.requireNonNull(language, "language"));
	    o.setConsumerName(MyStrings.requireNonEmpty(name, "name"));
	    o.setExternalId(externalId);

	    MyCollections.requireNonEmpty(items, "items") //
		    .stream() //
		    .forEach(x -> factory.generateNewOrderItem(x.product, x.cost, x.quantity, o));

	    return new PaymentImpl(o);
	}

	public final class PaymentImpl implements Payment {

	    private final KKBOrder order;
	    private KKBOrder accepted;

	    private PaymentImpl(KKBOrder order) {
		this.order = MyObjects.requireNonNull(order, "order");
	    }

	    @Override
	    public Ebill accept() {
		if (accepted != null)
		    throw new IllegalStateException("Already acceted");

		composer.composeCart(order);
		composer.composeRequest(order);

		accepted = orderDAO.save(order);

		notifier.assignOrderNotification(KKBNotificationChannel.EMAIL, //
			KKBNotificationRecipientType.REQUESTER, //
			KKBNotificationRequestStage.PAYMENT_LINK, //
			accepted);

		return new EbillBuilderImpl() //
			.withKKBOrder(order)
			.build();
	    }
	}
    }

    @Override
    public EbillBuilder newEbillBuilder() {
	return new EbillBuilderImpl();
    }

    public final class EbillBuilderImpl implements EbillBuilder {
	private EbillBuilderImpl() {
	}

	private String id;
	private String externalId;
	private EbillStatus status;
	private Instant created;
	private Double amount;
	private LocalizationLanguage consumerLanguage;
	private String consumerEmail;

	private Instant paid;
	private String reference;

	private List<EbillItemImpl> items;

	private Ebill ebill;
	private String requestContent;
	private String consumerName;
	private String requestAppendix;
	private URI postbackURI;

	@Override
	public EbillBuilder withFetched(String id) {
	    try {
		KKBOrder kkbOrder = orderDAO.findByIdByPassCache(MyStrings.requireNonEmpty(id, "id"));
		return withKKBOrder(kkbOrder);
	    } catch (KKBEntityNotFound e) {
		throw new IllegalArgumentException("not found", e);
	    }

	}

	@Override
	public EbillBuilder withPostbackURI(URI postbackURI) {
	    this.postbackURI = postbackURI;
	    return this;
	}

	EbillBuilder withKKBOrder(KKBOrder kkbOrder) {
	    this.id = kkbOrder.getId();
	    this.externalId = kkbOrder.getExternalId();
	    this.created = kkbOrder.getCreated();
	    this.amount = kkbOrder.getAmount();
	    this.consumerLanguage = kkbOrder.getConsumerLanguage();
	    this.consumerEmail = kkbOrder.getConsumerEmail();
	    this.consumerName = kkbOrder.getConsumerName();

	    this.items = kkbOrder.getItems().stream() //
		    .map(x -> new EbillItemImpl(x.getName(), x.getCost(), x.getQuantity()))
		    .collect(Collectors.toList());

	    this.requestContent = kkbOrder.getLastRequest().getContentBase64();
	    this.requestAppendix = kkbOrder.getLastCart().getContentBase64();
	    this.paid = kkbOrder.getPaid();
	    this.reference = kkbOrder.getPaymentReference();

	    switch (kkbOrder.getStatus()) {
	    case NEW:
		this.status = EbillStatus.READY;
		break;
	    case AUTHORIZATION_FAILED:
		this.status = EbillStatus.FAILED;
		break;
	    case CANCELED:
		this.status = EbillStatus.CANCELED;
		break;
	    case COMPLETED:
	    case AUTHORIZATION_PASS:
	    case ENROLLED:
		this.status = EbillStatus.PAID;
		break;
	    default:
	    }
	    return this;
	}

	@Override
	public Ebill build() {
	    if (ebill != null)
		throw new IllegalStateException("Already built");
	    switch (status) {
	    case READY:
		HttpFormTemplateImpl form = new HttpFormTemplateImpl(epayConfig.getEpayURL(), "POST",
			MyMaps.of( //
				"Signed_Order_B64", requestContent, //
				"template", epayConfig.getTemplateName(), //
				"email", consumerEmail, //
				"PostLink", postbackURI.toString(), // TODO move
								    // QAZKOM WS
								    // POSTBACK
								    // to own
				"Language", "%%LANGUAGE_TAG%%", //
				"appendix", requestAppendix, //
				"BackLink", "%%PAYMENT_PAGE_URL%%" //
			));
		ebill = new EbillImpl(id, externalId, status, created, amount, consumerLanguage, consumerEmail,
			consumerName, items,
			form);
		break;
	    case PAID:
		ebill = new EbillImpl(id, externalId, status, created, amount, consumerLanguage, consumerEmail,
			consumerName, items,
			paid,
			reference);
	    default:
	    }
	    return ebill;
	}

    }

    public final class EbillImpl implements Ebill {

	private final String id;
	private final String externalId;
	private final EbillStatus status;
	private final Instant created;
	private final Double amount;
	private final LocalizationLanguage consumerLanguage;
	private final String consumerEmail;
	private final String consumerName;

	private final List<EbillItemImpl> items;

	private final HttpFormTemplate form;

	private final Instant paid;
	private final String reference;

	// constructor for unpayed ebillImpl
	private EbillImpl(final String id, final String externalId, final EbillStatus status, final Instant created,
		final Double amount,
		final LocalizationLanguage consumerLanguage, final String consumerEmail, final String consumerName,
		List<EbillItemImpl> items, HttpFormTemplateImpl form) {

	    if (status != EbillStatus.READY)
		throw new IllegalArgumentException("Invalid status");

	    this.id = MyStrings.requireNonEmpty(id, "id");
	    this.externalId = externalId;
	    this.status = MyObjects.requireNonNull(status, "status");
	    this.created = MyObjects.requireNonNull(created, "created");
	    this.amount = MyNumbers.requireNonZero(amount, "amount");
	    this.consumerLanguage = MyObjects.requireNonNull(consumerLanguage, "userLanguage");
	    this.consumerEmail = MyStrings.requireNonEmpty(consumerEmail, "consumerEmail");
	    this.consumerName = MyStrings.requireNonEmpty(consumerName, "consumerName");

	    this.items = Collections.unmodifiableList(MyCollections.requireNonNullElements(items, "items"));

	    this.form = MyObjects.requireNonNull(form, "form");

	    this.paid = null;
	    this.reference = null;
	}

	// constructor for payed ebillImpl
	private EbillImpl(final String id, final String externalId, final EbillStatus status, final Instant created,
		final Double amount,
		final LocalizationLanguage userLanguage, final String consumerEmail, final String consumerName,
		final List<EbillItemImpl> items, final Instant paid,
		final String reference) {

	    if (status != EbillStatus.PAID)
		throw new IllegalArgumentException("Invalid status");

	    this.id = MyStrings.requireNonEmpty(id, "id");
	    this.externalId = externalId;
	    this.status = MyObjects.requireNonNull(status, "status");
	    this.created = MyObjects.requireNonNull(created, "created");
	    this.amount = MyNumbers.requireNonZero(amount, "amount");
	    this.consumerLanguage = MyObjects.requireNonNull(userLanguage, "userLanguage");
	    this.consumerEmail = MyStrings.requireNonEmpty(consumerEmail, "consumerEmail");
	    this.consumerName = MyStrings.requireNonEmpty(consumerName, "consumerName");

	    this.items = Collections.unmodifiableList(MyCollections.requireNonNullElements(items, "items"));

	    this.form = null;

	    this.paid = MyObjects.requireNonNull(paid, "paid");
	    this.reference = MyStrings.requireNonEmpty(reference, "reference");
	}

	@Override
	public String getId() {
	    return id;
	}

	@Override
	public EbillStatus getStatus() {
	    return status;
	}

	@Override
	public Instant getCreated() {
	    return created;
	}

	@Override
	public Double getAmount() {
	    return amount;
	}

	@Override
	public LocalizationLanguage getConsumerLanguage() {
	    return consumerLanguage;
	}

	@Override
	public List<? extends EbillItem> getItems() {
	    return items;
	}

	@Override
	public HttpFormTemplate getForm() {
	    return form;
	}

	@Override
	public Instant getPaid() {
	    return paid;
	}

	@Override
	public String getReference() {
	    return reference;
	}

	@Override
	public String getConsumerEmail() {
	    return consumerEmail;
	}

	@Override
	public String getConsumerName() {
	    return consumerName;
	}

	@Override
	public String getExternalId() {
	    return externalId;
	}

    }

    public static class HttpFormTemplateImpl implements HttpFormTemplate {

	private final URL url;
	private final String method;
	private final Map<String, String> params;

	HttpFormTemplateImpl(URL url, String method, Map<String, String> params) {
	    this.url = MyObjects.requireNonNull(url, "url");
	    this.method = MyStrings.requireNonEmpty(method, "method");
	    this.params = Collections.unmodifiableMap(MyMaps.requireNonEmpty(params, "params"));
	}

	@Override
	public URL getURL() {
	    return url;
	}

	@Override
	public String getMethod() {
	    return method;
	}

	@Override
	public Map<String, String> getParams() {
	    return params;
	}
    }

    public final class EbillItemImpl implements EbillItem {

	private final String name;
	private final Double amount;
	private final Integer quantity;

	private EbillItemImpl(String name, Double amount, Integer quantity) {
	    this.name = MyStrings.requireNonEmpty(name, "name");
	    this.amount = MyNumbers.requireNonZero(amount, "amount");
	    this.quantity = MyNumbers.requireNonZero(quantity, "quantity");
	}

	@Override
	public String getName() {
	    return name;
	}

	@Override
	public Double getAmount() {
	    return amount;
	}

	@Override
	public Integer getQuantity() {
	    return quantity;
	}
    }
}
