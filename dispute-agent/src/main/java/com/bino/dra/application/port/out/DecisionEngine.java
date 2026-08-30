package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;

import java.util.List;

public interface DecisionEngine {

    DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages);
}
