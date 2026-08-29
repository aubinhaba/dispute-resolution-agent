package com.bino.dra.application.port.in;

import com.bino.dra.domain.model.Dispute;

// Breaks a real cycle: submission needs the dispatcher, the dispatcher calls back to run the work
public interface DisputeJobRunner {

    void run(Dispute dispute);
}
