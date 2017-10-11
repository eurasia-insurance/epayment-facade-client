package tech.lapsa.epayment.facade;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import tech.lapsa.java.commons.function.MyCollections;
import tech.lapsa.java.commons.function.MyNumbers;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

public final class Ebill implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum EbillStatus {
	READY, CANCELED, PAID, FAILED
    }

    public final static class EbillItem implements Serializable {
	private static final long serialVersionUID = 1L;

	public static EbillItem newItem(String name, Double price, Integer quantity, Double totalAmount) {
	    return new EbillItem(name, price, quantity, totalAmount);
	}

	public static EbillItem newItem(String name, Double price, Integer quantity) {
	    return new EbillItem(name, price, quantity, price * quantity);
	}

	private final String name;
	private final Double price;
	private final Integer quantity;
	private final Double totalAmount;

	private EbillItem(String name, Double price, Integer quantity, Double totalAmount) {
	    this.name = MyStrings.requireNonEmpty(name, "name");
	    this.price = MyNumbers.requireNonZero(price, "price");
	    this.quantity = MyNumbers.requireNonZero(quantity, "quantity");
	    this.totalAmount = MyNumbers.requireNonZero(totalAmount, "amount");
	}

	public String getName() {
	    return name;
	}

	public Double getPrice() {
	    return price;
	}

	public Double getTotalAmount() {
	    return totalAmount;
	}

	public Integer getQuantity() {
	    return quantity;
	}
    }

    public static Ebill newUnpaid(final String id, final String externalId, final EbillStatus status,
	    final Instant created,
	    final Double amount, final String consumerEmail, final String consumerName, List<EbillItem> items) {
	return new Ebill(id, externalId, status, created, amount, consumerEmail, consumerName, items);
    }

    public static Ebill newPaid(final String id, final String externalId, final EbillStatus status,
	    final Instant created, final Double amount, final String consumerEmail, final String consumerName,
	    List<EbillItem> items, final Instant paid, final String reference) {
	return new Ebill(id, externalId, status, created, amount, consumerEmail, consumerName, items, paid, reference);
    }

    private final String id;
    private final String externalId;
    private final EbillStatus status;
    private final Instant created;
    private final Double amount;
    private final String consumerEmail;
    private final String consumerName;

    private final List<EbillItem> items;

    private final Instant paid;
    private final String reference;

    // constructor for unpayed ebillImpl
    private Ebill(final String id, final String externalId, final EbillStatus status, final Instant created,
	    final Double amount, final String consumerEmail, final String consumerName, List<EbillItem> items) {

	if (status != EbillStatus.READY)
	    throw new IllegalArgumentException("Invalid status");

	this.id = MyStrings.requireNonEmpty(id, "id");
	this.externalId = externalId;
	this.status = MyObjects.requireNonNull(status, "status");
	this.created = MyObjects.requireNonNull(created, "created");
	this.amount = MyNumbers.requireNonZero(amount, "amount");
	this.consumerEmail = MyStrings.requireNonEmpty(consumerEmail, "consumerEmail");
	this.consumerName = MyStrings.requireNonEmpty(consumerName, "consumerName");

	this.items = Collections.unmodifiableList(MyCollections.requireNonNullElements(items, "items"));

	this.paid = null;
	this.reference = null;
    }

    // constructor for payed ebillImpl
    private Ebill(final String id, final String externalId, final EbillStatus status, final Instant created,
	    final Double amount, final String consumerEmail, final String consumerName,
	    final List<EbillItem> items, final Instant paid, final String reference) {

	if (status != EbillStatus.PAID)
	    throw new IllegalArgumentException("Invalid status");

	this.id = MyStrings.requireNonEmpty(id, "id");
	this.externalId = externalId;
	this.status = MyObjects.requireNonNull(status, "status");
	this.created = MyObjects.requireNonNull(created, "created");
	this.amount = MyNumbers.requireNonZero(amount, "amount");
	this.consumerEmail = MyStrings.requireNonEmpty(consumerEmail, "consumerEmail");
	this.consumerName = MyStrings.requireNonEmpty(consumerName, "consumerName");

	this.items = Collections.unmodifiableList(MyCollections.requireNonNullElements(items, "items"));

	this.paid = MyObjects.requireNonNull(paid, "paid");
	this.reference = MyStrings.requireNonEmpty(reference, "reference");
    }

    public String getId() {
	return id;
    }

    public EbillStatus getStatus() {
	return status;
    }

    public Instant getCreated() {
	return created;
    }

    public Double getAmount() {
	return amount;
    }

    public List<EbillItem> getItems() {
	return items;
    }

    public Instant getPaid() {
	return paid;
    }

    public String getReference() {
	return reference;
    }

    public String getConsumerEmail() {
	return consumerEmail;
    }

    public String getConsumerName() {
	return consumerName;
    }

    public String getExternalId() {
	return externalId;
    }
}