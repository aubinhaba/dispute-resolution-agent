package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;

import java.util.List;

public interface DecisionEngine {

    DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages);

    // Throws rather than delegating to decide(): ignoring objections would pass an unrevised decision off as revised
    default DisputeDecision revise(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages,
                                   DisputeDecision previous, List<String> objections) {
        throw new UnsupportedOperationException("this decision engine cannot revise");
    }
}
