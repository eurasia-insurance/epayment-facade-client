package tech.lapsa.epayment.facade;

import java.net.URI;
import java.time.Instant;
import java.util.Currency;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;

@Local
public interface EpaymentFacade {

    URI getDefaultPaymentURI(String invoiceNumber) throws IllegalArgument, IllegalState;

    String invoiceAccept(InvoiceBuilder invoiceBuilder) throws IllegalArgument, IllegalState;

    Invoice getInvoiceByNumber(String invoiceNumber) throws IllegalArgument, IllegalState;

    boolean hasInvoiceWithNumber(String invoiceNumber) throws IllegalArgument, IllegalState;

    // qazkom type

    String processQazkomFailure(String failureXml) throws IllegalArgument, IllegalState;

    PaymentMethod qazkomHttpMethod(URI postbackURI, URI failureURI, URI returnURI, Invoice forInvoice)
	    throws IllegalArgument, IllegalState;

    void completeWithQazkomPayment(String postbackXml) throws IllegalArgument, IllegalState;

    // unknown type

    void completeWithUnknownPayment(String invoiceNumber, Double paidAmount, Currency paidCurency, Instant paidInstant,
	    String paidReference) throws IllegalArgument, IllegalState;

}