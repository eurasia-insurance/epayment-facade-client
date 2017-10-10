package com.lapsa.epayment.facade;

import java.time.Instant;
import java.util.List;

public interface Ebill {

    public static enum EbillStatus {
	READY, CANCELED, PAID, FAILED
    }

    public static interface EbillItem {

	String getName();

	Double getAmount();

	Integer getQuantity();

    }

    String getId();

    Instant getCreated();

    Double getAmount();

    EbillStatus getStatus();

    String getConsumerEmail();

    String getConsumerName();

    List<? extends EbillItem> getItems();

    Instant getPaid();

    String getReference();

    String getExternalId();

}