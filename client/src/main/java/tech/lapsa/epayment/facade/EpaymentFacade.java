package tech.lapsa.epayment.facade;

import java.net.URI;
import java.util.Optional;

import javax.ejb.Local;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.domain.Payment;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.java.commons.function.MyOptionals;

@Local
public interface EpaymentFacade {

    URI getDefaultPaymentURI(Invoice invoice) throws IllegalArgument, IllegalState;

    Invoice accept(Invoice invoice) throws IllegalArgument, IllegalState;

    Invoice completeAndAccept(InvoiceBuilder invoiceBuilder) throws IllegalArgument, IllegalState;

    Invoice invoiceByNumber(String number) throws IllegalArgument, IllegalState, InvoiceNotFound;

    default Optional<Invoice> optionalByNumber(String number) throws IllegalArgument, IllegalState {
	try {
	    return MyOptionals.of(invoiceByNumber(number));
	} catch (InvoiceNotFound e) {
	    return Optional.empty();
	}
    }

    default boolean hasInvoiceWithNumber(String number) throws IllegalArgument, IllegalState {
	return optionalByNumber(number) //
		.isPresent();
    }

    void invoiceHasPaidBy(Invoice invoice, Payment payment) throws IllegalArgument, IllegalState;
}