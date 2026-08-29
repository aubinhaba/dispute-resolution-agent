package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;

public interface DisputeJobDispatcher {

    // Takes a Dispute and not a Runnable: a Runnable cannot be implemented over a message queue
    void dispatch(Dispute dispute);
}
