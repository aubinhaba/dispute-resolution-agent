package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Network;

import java.util.List;

public interface RuleRetriever {

    List<String> retrieveRulePassages(String reasonCode, Network network);
}
