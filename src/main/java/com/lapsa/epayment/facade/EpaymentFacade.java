package com.lapsa.epayment.facade;

import javax.ejb.Local;

import com.lapsa.epayment.facade.beans.EpaymentFacadeBean.EbillBuilder;

@Local
public interface EpaymentFacade {

    PaymentBuilder newPaymentBuilder();

    EbillBuilder newEbillBuilder();

}