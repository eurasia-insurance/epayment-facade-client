package tech.lapsa.epayment.facade.producer.remote;

import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import tech.lapsa.epayment.facade.EJBViaCDI;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeRemote;

@Dependent
public class EpaymentFacadeProducer {

    @EJB
    private EpaymentFacadeRemote ejb;

    @Produces
    @EJBViaCDI
    public EpaymentFacade getEjb() {
	return ejb;
    }
}
