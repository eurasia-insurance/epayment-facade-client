package tech.lapsa.epayment.facade.beans;

import java.net.URI;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Provider;

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
public class QazkomFacadeDelegatingCDIBean implements QazkomFacade {

    @Inject
    private Provider<QazkomFacade> delegateProvider;

    @Override
    public QazkomPayment processPostback(final String postbackXml) throws IllegalArgument, IllegalState {
	return delegateProvider.get().processPostback(postbackXml);
    }

    @Override
    public QazkomError processFailure(final String failureXml) throws IllegalArgument, IllegalState {
	return delegateProvider.get().processFailure(failureXml);
    }

    @Override
    public PaymentMethod httpMethod(final URI postbackURI, final URI failureURI, final URI returnURI,
	    final Invoice forInvoice) throws IllegalArgument, IllegalState {
	return delegateProvider.get().httpMethod(postbackURI, failureURI, returnURI, forInvoice);
    }
}
