package com.lapsa.kkb.facade;

import java.util.ArrayList;
import java.util.Date;
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
import com.lapsa.kkb.core.KKBPaymentStatus;
import com.lapsa.kkb.dao.KKBOrderDAO;
import com.lapsa.kkb.mesenger.KKBNotificationChannel;
import com.lapsa.kkb.mesenger.KKBNotificationRecipientType;
import com.lapsa.kkb.mesenger.KKBNotificationRequestStage;
import com.lapsa.kkb.mesenger.KKBNotifier;
import com.lapsa.kkb.services.KKBDocumentComposerService;
import com.lapsa.kkb.services.KKBFactory;

@ApplicationScoped
public class QazkomFacade {

    @Inject
    private KKBDocumentComposerService composer;

    @Inject
    private KKBFactory factory;

    @Inject
    private KKBOrderDAO dao;

    @Inject
    private KKBNotifier notifier;

    public PaymentBuilder newPaymentBuilder() {
	return new PaymentBuilder();
    }

    public final class PaymentBuilder {
	private List<BuilderItem> items = new ArrayList<>();
	private String orderId;
	private String email;
	private LocalizationLanguage language;
	private String name;
	private String externalId;
	private FinCurrency currency;
	private KKBOrder accepted;

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

	public AcceptResult accept() {
	    return new AcceptResult(acceptAndReply().getId());
	}

	// PRIVATE

	private KKBOrder acceptAndReply() {
	    if (accepted != null)
		throw new IllegalStateException("Already acceted");

	    KKBOrder o = new KKBOrder();
	    o.setId(MyStrings.requireNonEmpty(orderId, "orderId"));
	    o.setCreated(new Date());
	    o.setStatus(KKBPaymentStatus.NEW);
	    o.setCurrency(Objects.requireNonNull(currency, "currency"));
	    o.setConsumerEmail(MyStrings.requireNonEmpty(email, "email"));
	    o.setConsumerLanguage(Objects.requireNonNull(language, "language"));
	    o.setConsumerName(MyStrings.requireNonEmpty(name, "name"));
	    o.setExternalId(externalId);

	    MyCollections.requireNonEmpty(items, "items is empty") //
		    .stream() //
		    .forEach(x -> factory.generateNewOrderItem(x.product, x.cost, x.quantity, o));

	    composer.composeCart(o);
	    composer.composeRequest(o);

	    accepted = dao.save(o);

	    notifier.assignOrderNotification(KKBNotificationChannel.EMAIL, //
		    KKBNotificationRecipientType.REQUESTER, //
		    KKBNotificationRequestStage.PAYMENT_LINK, accepted);

	    return accepted;
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

	public final class AcceptResult {
	    private final String reference;

	    private AcceptResult(String reference) {
		this.reference = reference;
	    }

	    public String getReference() {
		return reference;
	    }
	}

    }
}
