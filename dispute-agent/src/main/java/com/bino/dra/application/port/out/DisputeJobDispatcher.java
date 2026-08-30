package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;

public interface DisputeJobDispatcher {

    void dispatch(Dispute dispute);
}
