package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.Decision;

import java.util.List;

public record DecisionDraft(
        Decision decision,
        double confidence,
        String rationale,
        String citedReasonCode,
        List<String> citedRulePassages,
        List<String> evidenceRefs
) {
}
