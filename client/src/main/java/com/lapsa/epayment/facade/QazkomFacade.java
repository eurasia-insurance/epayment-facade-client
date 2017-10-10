package com.lapsa.epayment.facade;

import javax.ejb.Remote;

@Remote
public interface QazkomFacade {

    ResponseBuilder newResponseBuilder();

    public static interface ResponseBuilder {

	ResponseBuilder withXml(String responseXml);

	Response build();

	public static interface Response {

	    Ebill handle();

	}

    }
}