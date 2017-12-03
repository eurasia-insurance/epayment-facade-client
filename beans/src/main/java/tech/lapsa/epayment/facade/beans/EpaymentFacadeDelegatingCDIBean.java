package tech.lapsa.epayment.facade.beans;

import java.net.URI;

import javax.ejb.EJB;
import javax.enterprise.context.Dependent;

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

    @EJB
    private EpaymentFacade delegate;

    @Override
    public URI getDefaultPaymentURI(final Invoice invoice) throws IllegalArgument, IllegalState {
	return delegate.getDefaultPaymentURI(invoice);
    }

    @Override
    public Invoice accept(final Invoice invoice) throws IllegalArgument, IllegalState {
	return delegate.accept(invoice);
    }

    @Override
    public Invoice completeAndAccept(final InvoiceBuilder invoiceBuilder) throws IllegalArgument, IllegalState {
	return delegate.completeAndAccept(invoiceBuilder);
    }

    @Override
    public Invoice invoiceByNumber(final String number) throws IllegalArgument, IllegalState, InvoiceNotFound {
	return delegate.invoiceByNumber(number);
    }

    @Override
    public void invoiceHasPaidBy(final Invoice invoice, final Payment payment) throws IllegalArgument, IllegalState {
	delegate.invoiceHasPaidBy(invoice, payment);
    }
}
