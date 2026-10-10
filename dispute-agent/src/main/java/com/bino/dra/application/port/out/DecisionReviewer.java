package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.ReviewVerdict;

import java.util.List;

public interface DecisionReviewer {

    ReviewVerdict review(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages,
                         DisputeDecision proposed);
}
