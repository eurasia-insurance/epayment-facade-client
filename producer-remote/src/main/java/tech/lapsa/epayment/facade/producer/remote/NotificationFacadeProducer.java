package tech.lapsa.epayment.facade.producer.remote;

import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import tech.lapsa.epayment.facade.EJBViaCDI;
import tech.lapsa.epayment.facade.NotificationFacade;
import tech.lapsa.epayment.facade.NotificationFacade.NotificationFacadeRemote;

@Dependent
public class NotificationFacadeProducer {

    @EJB
    private NotificationFacadeRemote ejb;

    @Produces
    @EJBViaCDI
    public NotificationFacade getEjb() {
	return ejb;
    }
}
