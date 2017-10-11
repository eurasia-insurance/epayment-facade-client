package tech.lapsa.epayment.facade;

import com.lapsa.fin.FinCurrency;
import com.lapsa.international.localization.LocalizationLanguage;

public interface EpaymentFacade {

    EbillAcceptorBuilder newEbillAcceptorBuilder();

    public static interface EbillAcceptorBuilder {

	EbillAcceptorBuilder withMoreItem(String productName, double cost, int quantity);

	EbillAcceptorBuilder winthGeneratedId();

	EbillAcceptorBuilder withOrderCurrencty(FinCurrency currency);

	EbillAcceptorBuilder withDefaultCurrency();

	EbillAcceptorBuilder withId(String orderId);

	EbillAcceptorBuilder withConsumer(String email, LocalizationLanguage language, String name);

	EbillAcceptorBuilder withConsumer(String email, LocalizationLanguage language);

	EbillAcceptorBuilder withConsumerName(String name);

	EbillAcceptorBuilder withConsumerEmail(String email);

	EbillAcceptorBuilder withExternalId(String externalId);

	EbillAcceptorBuilder withExternalId(Integer externalId);

	EbillAcceptorBuilder withConsumerLanguage(LocalizationLanguage language);

	EbillAcceptor build();

	public static interface EbillAcceptor {

	    Ebill accept();

	}
    }

    EbillFetcherBuilder newEbillFetcherBuilder();

    public static interface EbillFetcherBuilder {

	EbillFetcherBuilder usingId(String id);

	EbillFetcher build();

	public static interface EbillFetcher {

	    Ebill fetch();

	}

    }

}