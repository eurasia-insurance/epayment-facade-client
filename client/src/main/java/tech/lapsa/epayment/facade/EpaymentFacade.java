package tech.lapsa.epayment.facade;

import java.net.URI;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;

@Local
public interface EpaymentFacade {

    URI getDefaultPaymentURI(Invoice invoice) throws IllegalArgument, IllegalState;

    Invoice accept(Invoice invoice) throws IllegalArgument, IllegalState;

    Invoice completeAndAccept(InvoiceBuilder invoiceBuilder) throws IllegalArgument, IllegalState;

    Invoice forNumber(String number) throws IllegalArgument, IllegalState, InvoiceNotFound;

    void completeAfterPayment(Invoice invoice) throws IllegalArgument, IllegalState;
}