package com.lapsa.epayment.facade;

import java.time.Instant;
import java.util.List;

import com.lapsa.epayment.facade.beans.EpaymentFacadeBean.EbillStatus;
import com.lapsa.epayment.facade.beans.EpaymentFacadeBean.HttpFormTemplate;
import com.lapsa.international.localization.LocalizationLanguage;

public interface Ebill {

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