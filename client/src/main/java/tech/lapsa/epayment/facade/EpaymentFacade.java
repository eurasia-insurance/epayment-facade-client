package tech.lapsa.epayment.facade;

import java.net.URI;
import java.time.Instant;
import java.util.Currency;

import javax.ejb.Local;
import javax.ejb.Remote;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.exceptions.IllegalState;

public interface EpaymentFacade extends EJBConstants {

    @Local
    public interface EpaymentFacadeLocal extends EpaymentFacade {
    }

    @Remote
    public interface EpaymentFacadeRemote extends EpaymentFacade {
    }

    URI getDefaultPaymentURI(String invoiceNumber) throws IllegalArgument, InvoiceNotFound;

    Invoice getInvoiceByNumber(String invoiceNumber) throws IllegalArgument, InvoiceNotFound;

    boolean hasInvoiceWithNumber(String invoiceNumber) throws IllegalArgument;

    String invoiceAccept(InvoiceBuilder invoiceBuilder) throws IllegalArgument;

    // qazkom type

    String processQazkomFailure(String failureXml) throws IllegalArgument;

    PaymentMethod qazkomHttpMethod(URI postbackURI, URI failureURI, URI returnURI, Invoice forInvoice)
	    throws IllegalArgument;

    PaymentMethod qazkomHttpMethod(URI postbackURI, URI failureURI, URI returnURI, String invoiceNumber)
	    throws IllegalArgument, InvoiceNotFound;

    void completeWithQazkomPayment(String postbackXml) throws IllegalArgument, IllegalState;

    // unknown type

    void completeWithUnknownPayment(String invoiceNumber, Double paidAmount, Currency paidCurency, Instant paidInstant,
	    String paidReference) throws IllegalArgument, IllegalState, InvoiceNotFound;

}