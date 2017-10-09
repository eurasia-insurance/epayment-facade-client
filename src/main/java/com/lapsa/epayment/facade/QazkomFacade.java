package com.lapsa.epayment.facade;

import javax.ejb.Local;

@Local
public interface QazkomFacade {

    ResponseBuilder newResponseBuilder();

}