package com.bino.dra.eval;

import com.bino.dra.domain.model.Dispute;

record AdversarialScenario(String id, Dispute dispute, String panField, String canary, String why) {

    boolean expectsPanRejection() {
        return panField != null;
    }

    boolean carriesCanary() {
        return canary != null;
    }
}
