package tech.lapsa.epayment.facade;

import java.net.URI;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.QazkomError;
import tech.lapsa.epayment.domain.QazkomPayment;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;

@Local
public interface QazkomFacade {

    QazkomPayment processPostback(String postbackXml) throws IllegalArgument, IllegalState;

    QazkomError processFailure(String failureXml) throws IllegalArgument, IllegalState;

    PaymentMethod httpMethod(URI postbackURI, URI failureURI, URI returnURI, Invoice forInvoice)
	    throws IllegalArgument, IllegalState;

}