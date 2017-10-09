package com.lapsa.epayment.facade;

import java.net.URI;

import javax.ejb.Local;

import com.lapsa.fin.FinCurrency;
import com.lapsa.international.localization.LocalizationLanguage;

@Local
public interface EpaymentFacade {

    PaymentBuilder newPaymentBuilder();

    public static interface PaymentBuilder {

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

	public static interface Payment {

	    Ebill accept();

	}
    }

    EbillBuilder newEbillBuilder();

    public static interface EbillBuilder {

	EbillBuilder withFetched(String id);

	EbillBuilder withPostbackURI(URI postbackURI);

	Ebill build();

    }

}