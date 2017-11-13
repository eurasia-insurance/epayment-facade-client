package tech.lapsa.epayment.facade;

import java.net.URI;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;

@Local
public interface QazkomFacade {
    Invoice handleResponse(String responseXml);

    PaymentMethod httpMethod(URI postbackURI, URI returnUri, Invoice forInvoice);
}