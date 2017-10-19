package tech.lapsa.epayment.facade.beans;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;
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
import com.lapsa.international.localization.LocalizationLanguage;
import com.lapsa.kkb.core.KKBOrder;
import com.lapsa.kkb.core.KKBPaymentStatus;
import com.lapsa.kkb.services.KKBDocumentComposerService;
import com.lapsa.kkb.services.KKBFactory;

import tech.lapsa.epayment.dao.KKBOrderDAO;
import tech.lapsa.epayment.facade.Ebill;
import tech.lapsa.epayment.facade.Ebill.EbillItem;
import tech.lapsa.epayment.facade.Ebill.EbillStatus;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.notifier.NotificationChannel;
import tech.lapsa.epayment.notifier.NotificationRecipientType;
import tech.lapsa.epayment.notifier.NotificationRequestStage;
import tech.lapsa.epayment.notifier.Notifier;
import tech.lapsa.java.commons.function.MyCollections;
import tech.lapsa.java.commons.function.MyNumbers;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

@Stateless
public class EpaymentFacadeBean implements EpaymentFacade {

    @Inject
    private KKBDocumentComposerService composer;

    @Inject
    private KKBFactory factory;

    @Inject
    private KKBOrderDAO orderDAO;

    @Inject
    private Notifier notifier;

    @Override
    public URI getDefaultPaymentURI(Ebill ebill) {
	return factory.generateDefaultPaymentURI(ebill.getId());
    }

    @Override
    public EbillAcceptorBuilder newEbillAcceptorBuilder() {
	return new EbillAcceptorBuilderImpl();
    }

    final class EbillAcceptorBuilderImpl implements EbillAcceptorBuilder {
	private List<BuilderItem> items = new ArrayList<>();
	private String orderId;
	private String email;
	private LocalizationLanguage language;
	private String name;
	private String externalId;
	private FinCurrency currency;

	private EbillAcceptorBuilderImpl() {
	}

	@Override
	public EbillAcceptorBuilder withMoreItem(String productName, double cost, int quantity) {
	    items.add(new BuilderItem(productName, cost, quantity));
	    return this;
	}

	@Override
	public EbillAcceptorBuilder winthGeneratedId() {
	    this.orderId = factory.generateNewOrderId();
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withOrderCurrencty(FinCurrency currency) {
	    this.currency = currency;
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withDefaultCurrency() {
	    this.currency = FinCurrency.KZT;
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withId(String orderId) {
	    this.orderId = orderId;
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withConsumer(String email, LocalizationLanguage language, String name) {
	    withConsumerLanguage(language);
	    withConsumerEmail(email);
	    withConsumerName(name);
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withConsumer(String email, LocalizationLanguage language) {
	    withConsumerLanguage(language);
	    withConsumerEmail(email);
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withConsumerName(String name) {
	    this.name = MyStrings.requireNonEmpty(name, "name");
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withConsumerEmail(String email) {
	    this.email = MyStrings.requireNonEmpty(email, "email");
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withExternalId(String externalId) {
	    this.externalId = MyStrings.requireNonEmpty(externalId, "externalId");
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withExternalId(Integer externalId) {
	    this.externalId = MyNumbers.requireNonZero(externalId, "externalId").toString();
	    return this;
	}

	@Override
	public EbillAcceptorBuilder withConsumerLanguage(LocalizationLanguage language) {
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
	public EbillAcceptor build() {
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

	    return new EbillAcceptorImpl(o);
	}

	final class EbillAcceptorImpl implements EbillAcceptor {

	    private boolean accepted = false;
	    final KKBOrder order;

	    private EbillAcceptorImpl(KKBOrder order) {
		this.order = MyObjects.requireNonNull(order, "order");
	    }

	    @Override
	    public Ebill accept() {
		if (accepted)
		    throw new IllegalStateException("Already accepted");

		composer.composeCart(order);
		composer.composeRequest(order);

		KKBOrder saved = orderDAO.save(order);

		notifier.newNotificationBuilder() //
			.withChannel(NotificationChannel.EMAIL) //
			.withEvent(NotificationRequestStage.PAYMENT_LINK) //
			.withRecipient(NotificationRecipientType.REQUESTER) //
			.forEpayment(saved) //
			.build() //
			.send();

		accepted = true;

		return new EbillFetcherBuilderImpl() //
			.withKKBOrder(saved) //
			.build() //
			.fetch();
	    }
	}

    }

    @Override
    public EbillFetcherBuilder newEbillFetcherBuilder() {
	return new EbillFetcherBuilderImpl();
    }

    final class EbillFetcherBuilderImpl implements EbillFetcherBuilder {

	private KKBOrder order;

	private EbillFetcherBuilderImpl() {
	}

	@Override
	public EbillFetcherBuilder usingId(String id) {
	    return withKKBOrder(orderDAO.optionalByIdByPassCache(MyStrings.requireNonEmpty(id, "id")) //
		    .orElseThrow(() -> new IllegalArgumentException("not found")));
	}

	EbillFetcherBuilder withKKBOrder(KKBOrder order) {
	    this.order = MyObjects.requireNonNull(order, "order");
	    return this;
	}

	@Override
	public EbillFetcher build() {
	    return new EbillFetcherImpl(order);
	}

	final class EbillFetcherImpl implements EbillFetcher {

	    private boolean fetched = false;
	    final KKBOrder order;

	    private EbillFetcherImpl(KKBOrder order) {
		this.order = MyObjects.requireNonNull(order, "order");
	    }

	    @Override
	    public Ebill fetch() {
		if (fetched)
		    throw new IllegalStateException("Already accepted");

		String id = order.getId();
		String externalId = order.getExternalId();
		Instant created = order.getCreated();
		double amount = order.getAmount();
		String consumerEmail = order.getConsumerEmail();
		String consumerName = order.getConsumerName();

		List<EbillItem> items = order.getItems().stream() //
			.map(x -> EbillItem.newItem(x.getName(), x.getCost(), x.getQuantity()))
			.collect(Collectors.toList());

		Instant paid = order.getPaid();
		String reference = order.getPaymentReference();

		EbillStatus status;
		switch (order.getStatus()) {
		case NEW:
		    status = EbillStatus.READY;
		    break;
		case AUTHORIZATION_FAILED:
		    status = EbillStatus.FAILED;
		    break;
		case CANCELED:
		    status = EbillStatus.CANCELED;
		    break;
		case COMPLETED:
		case AUTHORIZATION_PASS:
		case ENROLLED:
		    status = EbillStatus.PAID;
		    break;
		default:
		    throw new IllegalStateException("Illegal status of the payment");
		}

		Ebill ebill = null;

		switch (status) {
		case READY:
		case CANCELED:
		case FAILED:
		    ebill = Ebill.newUnpaid(id, externalId, status, created, amount, consumerEmail, consumerName,
			    items);
		    break;
		case PAID:
		    ebill = Ebill.newPaid(id, externalId, status, created, amount, consumerEmail, consumerName, items,
			    paid, reference);
		    break;
		default:
		    throw new IllegalStateException("Illegal status of the payment");
		}

		fetched = true;
		return ebill;
	    }
	}
    }

    private static final String JNDI_JMS_CONNECTION_FACTORY = "epayment/jms/connectionFactory";

    @Resource(name = JNDI_JMS_CONNECTION_FACTORY)
    private ConnectionFactory connectionFactory;

    public static final String JNDI_JMS_DEST_PAID_EBILLs = "epayment/jms/paidEbills";

    @Resource(name = JNDI_JMS_DEST_PAID_EBILLs)
    private Destination paidEbillsDestination;

    @Override
    public EbillPaidMarkerBuilder newEbillPaidMarkerBuilder() {
	return new EbillPaidMarkerBuilderImpl();
    }

    final class EbillPaidMarkerBuilderImpl implements EbillPaidMarkerBuilder {

	private KKBOrder order;
	private String reference;
	private Instant instant;

	private EbillPaidMarkerBuilderImpl() {
	}

	@Override
	public EbillPaidMarkerBuilder usingId(String id) {
	    return withKKBOrder(orderDAO.optionalByIdByPassCache(MyStrings.requireNonEmpty(id, "id")) //
		    .orElseThrow(() -> new IllegalArgumentException("not found")));
	}

	EbillPaidMarkerBuilder withKKBOrder(KKBOrder order) {
	    this.order = MyObjects.requireNonNull(order, "order");
	    return this;
	}

	@Override
	public EbillPaidMarkerBuilder withReference(String reference) {
	    this.reference = MyStrings.requireNonEmpty(reference, "reference");
	    return this;
	}

	@Override
	public EbillPaidMarkerBuilder withInstant(Instant instant) {
	    this.instant = MyObjects.requireNonNull(instant, "instant");
	    return this;
	}

	@Override
	public EbillPaidMarker build() {
	    return new EbillPaidMarkerImpl(order, reference, instant);
	}

	final class EbillPaidMarkerImpl implements EbillPaidMarker {
	    private boolean marked = false;

	    private final KKBOrder order;
	    private final String reference;
	    private final Instant instant;

	    private EbillPaidMarkerImpl(KKBOrder order, String reference, Instant instant) {
		this.order = MyObjects.requireNonNull(order, "order");
		this.reference = MyStrings.requireNonEmpty(reference, "reference");
		this.instant = MyObjects.requireNonNull(instant, "instant");
	    }

	    @Override
	    public Ebill mark() {
		if (marked)
		    throw new IllegalStateException("Already marked paid");

		order.setPaid(instant);
		order.setPaymentReference(reference);

		KKBOrder saved = orderDAO.save(order);

		notifier.assignOrderNotification(NotificationChannel.EMAIL, //
			NotificationRecipientType.REQUESTER, //
			NotificationRequestStage.PAYMENT_SUCCESS, //
			saved);

		Ebill ebill = new EbillFetcherBuilderImpl() //
			.withKKBOrder(saved) //
			.build() //
			.fetch();

		try (Connection connection = connectionFactory.createConnection()) {
		    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		    MessageProducer producer = session.createProducer(paidEbillsDestination);
		    Message msg = session.createObjectMessage(ebill);
		    producer.send(msg);
		} catch (JMSException e) {
		    throw new IllegalStateException("Failed to send paid ebill info", e);
		}

		marked = true;

		return ebill;
	    }
	}
    }
}
