package com.lapsa.epayment.facade;

public interface ResponseBuilder {

    ResponseBuilder withXml(String responseXml);

    Response build();

}