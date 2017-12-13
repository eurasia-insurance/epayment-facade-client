package tech.lapsa.epayment.facade;

import java.net.URI;
import java.time.Instant;
import java.util.Currency;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;

@Local
public interface EpaymentFacade {

    URI getDefaultPaymentURI(String invoiceNumber) throws IllegalArgumentException, InvoiceNotFound;

    Invoice getInvoiceByNumber(String invoiceNumber) throws IllegalArgumentException, InvoiceNotFound;

    boolean hasInvoiceWithNumber(String invoiceNumber) throws IllegalArgumentException;

    String invoiceAccept(InvoiceBuilder invoiceBuilder) throws IllegalArgumentException;

    // qazkom type

    String processQazkomFailure(String failureXml) throws IllegalArgumentException, IllegalStateException;

    PaymentMethod qazkomHttpMethod(URI postbackURI, URI failureURI, URI returnURI, Invoice forInvoice)
	    throws IllegalArgumentException;

    void completeWithQazkomPayment(String postbackXml) throws IllegalArgumentException, IllegalStateException;

    // unknown type

    void completeWithUnknownPayment(String invoiceNumber, Double paidAmount, Currency paidCurency, Instant paidInstant,
	    String paidReference) throws IllegalArgumentException, IllegalStateException;

}