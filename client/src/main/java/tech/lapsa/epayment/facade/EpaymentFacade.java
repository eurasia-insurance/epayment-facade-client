package tech.lapsa.epayment.facade;

import java.net.URI;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;

@Local
public interface EpaymentFacade {

    URI getDefaultPaymentURI(Invoice invoice) throws IllegalArgumentException;

    Invoice accept(Invoice invoice) throws IllegalArgumentException;

    Invoice completeAndAccept(InvoiceBuilder invoiceBuilder) throws IllegalArgumentException;

    Invoice forNumber(String number) throws IllegalArgumentException, InvoiceNotFound;

    // TODO REFACTOR : Need to rename to completePayment
    void markPaid(Invoice invoice) throws IllegalArgumentException;

}