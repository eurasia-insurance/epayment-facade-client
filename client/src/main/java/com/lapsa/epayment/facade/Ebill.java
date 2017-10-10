package com.lapsa.epayment.facade;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.lapsa.international.localization.LocalizationLanguage;

public interface Ebill {

    public static enum EbillStatus {
	READY, CANCELED, PAID, FAILED
    }

    public static interface HttpFormTemplate {

	URL getURL();

	String getMethod();

	Map<String, String> getParams();

    }

    public static interface EbillItem {

	String getName();

	Double getAmount();

	Integer getQuantity();

    }

    String getId();

    EbillStatus getStatus();

    Instant getCreated();

    Double getAmount();

    LocalizationLanguage getConsumerLanguage();

    List<? extends EbillItem> getItems();

    HttpFormTemplate getForm();

    Instant getPaid();

    String getReference();

    String getConsumerEmail();

    String getConsumerName();

    String getExternalId();

}