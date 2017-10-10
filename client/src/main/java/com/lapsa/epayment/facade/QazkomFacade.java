package com.lapsa.epayment.facade;

import javax.ejb.Remote;

@Remote
public interface QazkomFacade {

    ResponseBuilder newResponseBuilder();

}