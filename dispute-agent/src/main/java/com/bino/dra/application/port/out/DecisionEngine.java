package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.Transaction;

import java.util.List;


public interface DecisionEngine {

    DisputeDecision decide(Dispute dispute, List<Transaction> evidence, List<String> rulePassages);
}
