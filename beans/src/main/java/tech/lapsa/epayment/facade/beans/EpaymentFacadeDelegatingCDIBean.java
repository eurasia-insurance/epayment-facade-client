package tech.lapsa.epayment.facade.beans;

import java.net.URI;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Provider;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.domain.Payment;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.javax.cdi.qualifiers.QDelegateToEJB;

@Dependent
@QDelegateToEJB
public class EpaymentFacadeDelegatingCDIBean implements EpaymentFacade {

    @Inject
    private Provider<EpaymentFacade> delegateProvider;

    @Override
    public URI getDefaultPaymentURI(final Invoice invoice) throws IllegalArgument, IllegalState {
	return delegateProvider.get().getDefaultPaymentURI(invoice);
    }

    @Override
    public Invoice accept(final Invoice invoice) throws IllegalArgument, IllegalState {
	return delegateProvider.get().accept(invoice);
    }

    @Override
    public Invoice completeAndAccept(final InvoiceBuilder invoiceBuilder) throws IllegalArgument, IllegalState {
	return delegateProvider.get().completeAndAccept(invoiceBuilder);
    }

    @Override
    public Invoice invoiceByNumber(final String number) throws IllegalArgument, IllegalState, InvoiceNotFound {
	return delegateProvider.get().invoiceByNumber(number);
    }

    @Override
    public void invoiceHasPaidBy(final Invoice invoice, final Payment payment) throws IllegalArgument, IllegalState {
	delegateProvider.get().invoiceHasPaidBy(invoice, payment);
    }
}
