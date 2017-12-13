package tech.lapsa.epayment.facade.producer.local;

import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import tech.lapsa.epayment.facade.EJBViaCDI;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeLocal;

@Dependent
public class EpaymentFacadeProducer {

    @EJB
    private EpaymentFacadeLocal ejb;

    @Produces
    @EJBViaCDI
    public EpaymentFacade getEjb() {
	return ejb;
    }
}
