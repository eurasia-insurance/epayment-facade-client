package tech.lapsa.epayment.facade.drivenBeans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import java.net.URI;
import java.util.Properties;

import javax.ejb.MessageDriven;
import javax.inject.Inject;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.epayment.shared.entity.XmlPaymentURISpecifierRequest;
import tech.lapsa.epayment.shared.entity.XmlPaymentURISpecifierResponse;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.javax.jms.CallableServiceDrivenBean;

@MessageDriven(mappedName = EpaymentDestinations.SPECIFY_PAYMENT_URI)
public class PaymentURISpecifierDrivenBean
	extends CallableServiceDrivenBean<XmlPaymentURISpecifierRequest, XmlPaymentURISpecifierResponse> {

    public PaymentURISpecifierDrivenBean() {
	super(XmlPaymentURISpecifierRequest.class);
    }

    @Inject
    private EpaymentFacade epayments;

    @Override
    public XmlPaymentURISpecifierResponse calling(XmlPaymentURISpecifierRequest request, Properties properties) {
	return reThrowAsUnchecked(() -> {
	    try {
		final Invoice invoice = epayments.invoiceByNumber(request.getInvoiceNumber());
		final URI uri = epayments.getDefaultPaymentURI(invoice);
		return new XmlPaymentURISpecifierResponse(uri);
	    } catch (InvoiceNotFound e) {
		throw new RuntimeException(e);
	    }
	});
    }

}
