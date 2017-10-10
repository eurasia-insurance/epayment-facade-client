package com.lapsa.epayment.facade;

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