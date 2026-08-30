package com.bino.dra.eval;

import com.bino.dra.domain.model.Dispute;

record EvalScenario(EvalCase groundTruth, Dispute dispute, boolean deterministic, String why) {

    String id() {
        return groundTruth.disputeId();
    }
}
