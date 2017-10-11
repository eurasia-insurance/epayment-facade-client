package tech.lapsa.epayment.facade;

import java.net.URI;
import java.util.Map;

import javax.ejb.Local;

import com.lapsa.international.localization.LocalizationLanguage;

@Local
public interface QazkomFacade {

    ResponseHandlerBuilder newResponseHandlerBuilder();

    public static interface ResponseHandlerBuilder {

	ResponseHandlerBuilder withXml(String responseXml);

	ResponseHandler build();

	public static interface ResponseHandler {

	    Ebill handle();

	}

    }

    PaymentMethodBuilder newPaymentMethodBuilder();

    public static interface PaymentMethodBuilder {
	PaymentMethodBuilder withPostbackURI(URI postbackURL);

	PaymentMethodBuilder withReturnURI(URI returnUri);

	PaymentMethodBuilder withConsumerLanguage(LocalizationLanguage language);

	PaymentMethodBuilder forEbill(Ebill bill);

	PaymentMethod build();

	public static interface PaymentMethod {

	    HttpMethod getHttp();

	    public static interface HttpMethod {
		URI getHttpAddress();

		String getHttpMethod();

		Map<String, String> getHttpParams();
	    }
	}
    }

}