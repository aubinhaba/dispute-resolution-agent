package com.bino.dra.application.port.in;

import com.bino.dra.domain.model.Dispute;

public interface DisputeJobRunner {

    void run(Dispute dispute);
}
