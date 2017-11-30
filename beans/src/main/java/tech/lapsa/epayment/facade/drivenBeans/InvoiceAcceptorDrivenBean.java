package tech.lapsa.epayment.facade.drivenBeans;

import static tech.lapsa.java.commons.function.MyExceptions.*;

import java.util.Properties;

import javax.ejb.MessageDriven;
import javax.inject.Inject;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.shared.entity.XmlInvoiceAcceptRequest;
import tech.lapsa.epayment.shared.entity.XmlInvoiceAcceptResponse;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.function.MyStreams;
import tech.lapsa.javax.jms.service.JmsCallableServiceDrivenBean;

@MessageDriven(mappedName = EpaymentDestinations.ACCEPT_INVOICE)
public class InvoiceAcceptorDrivenBean
	extends JmsCallableServiceDrivenBean<XmlInvoiceAcceptRequest, XmlInvoiceAcceptResponse> {

    public InvoiceAcceptorDrivenBean() {
	super(XmlInvoiceAcceptRequest.class);
    }

    @Inject
    private EpaymentFacade epayments;

    @Override
    public XmlInvoiceAcceptResponse calling(XmlInvoiceAcceptRequest entity, Properties properties) {
	final InvoiceBuilder builder = Invoice.builder() //
		.withGeneratedNumber() //
		.withConsumerName(entity.getName()) //
		.withCurrency(entity.getCurrency()) //
		.withConsumerPreferLanguage(entity.getLanguage())
		//
		.withExternalId(entity.optExternalId()) //
		.withConsumerEmail(entity.optEmail()) //
		.withConsumerPhone(entity.optPhoneNumber()) //
		.withConsumerTaxpayerNumber(entity.optTaxpayerNumber());

	MyStreams.orEmptyOf(entity.getItems()) //
		.forEach(x -> builder.withItem(x.getTitle(), x.getQuantity(), x.getPrice()));

	final String invoiceNumber = reThrowAsUnchecked(() -> epayments.completeAndAccept(builder) //
		.getNumber());

	return new XmlInvoiceAcceptResponse(invoiceNumber);
    }

}
