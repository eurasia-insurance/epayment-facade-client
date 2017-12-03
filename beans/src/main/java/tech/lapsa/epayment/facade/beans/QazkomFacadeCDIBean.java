package tech.lapsa.epayment.facade.beans;

import java.net.URI;

import javax.ejb.EJB;
import javax.enterprise.context.Dependent;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.QazkomError;
import tech.lapsa.epayment.domain.QazkomPayment;
import tech.lapsa.epayment.facade.PaymentMethod;
import tech.lapsa.epayment.facade.QazkomFacade;
import tech.lapsa.java.commons.function.MyExceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions.IllegalState;
import tech.lapsa.javax.cdi.qualifiers.QDelegateToEJB;

@Dependent
@QDelegateToEJB
public class QazkomFacadeCDIBean implements QazkomFacade {

    @EJB
    private QazkomFacade delegate;

    @Override
    public QazkomPayment processPostback(final String postbackXml) throws IllegalArgument, IllegalState {
	return delegate.processPostback(postbackXml);
    }

    @Override
    public QazkomError processFailure(final String failureXml) throws IllegalArgument, IllegalState {
	return delegate.processFailure(failureXml);
    }

    @Override
    public PaymentMethod httpMethod(final URI postbackURI, final URI failureURI, final URI returnURI,
	    final Invoice forInvoice)
	    throws IllegalArgument, IllegalState {
	return delegate.httpMethod(postbackURI, failureURI, returnURI, forInvoice);
    }
}
