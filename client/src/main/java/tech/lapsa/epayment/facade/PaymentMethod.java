package tech.lapsa.epayment.facade;

import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

public interface PaymentMethod extends Serializable {

    Http getHttp();

    public static final class Http implements Serializable {

	private static final long serialVersionUID = 1L;

	final URI httpAddress;
	final String httpMethod;
	final Map<String, String> httpParams;

	public Http(URI httpAddress, String httpMethod, Map<String, String> httpParams) {
	    this.httpAddress = MyObjects.requireNonNull(httpAddress, "httpAddress");
	    this.httpMethod = MyStrings.requireNonEmpty(httpMethod, "httpMethod");
	    this.httpParams = Collections.unmodifiableMap(MyObjects.requireNonNull(httpParams, "httpParams"));
	}

	public URI getHttpAddress() {
	    return httpAddress;
	}

	public String getHttpMethod() {
	    return httpMethod;
	}

	public Map<String, String> getHttpParams() {
	    return httpParams;
	}
    }
}
