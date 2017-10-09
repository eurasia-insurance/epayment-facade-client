package com.lapsa.epayment.facade;

import com.lapsa.fin.FinCurrency;
import com.lapsa.international.localization.LocalizationLanguage;

public interface PaymentBuilder {

    PaymentBuilder withMoreItem(String productName, double cost, int quantity);

    PaymentBuilder winthGeneratedId();

    PaymentBuilder withOrderCurrencty(FinCurrency currency);

    PaymentBuilder withDefaultCurrency();

    PaymentBuilder withId(String orderId);

    PaymentBuilder withConsumer(String email, LocalizationLanguage language, String name);

    PaymentBuilder withConsumer(String email, LocalizationLanguage language);

    PaymentBuilder withConsumerName(String name);

    PaymentBuilder withConsumerEmail(String email);

    PaymentBuilder withExternalId(String externalId);

    PaymentBuilder withExternalId(Integer externalId);

    PaymentBuilder withConsumerLanguage(LocalizationLanguage language);

    Payment build();

}