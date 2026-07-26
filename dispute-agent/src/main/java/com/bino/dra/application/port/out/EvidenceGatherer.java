package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.EvidenceBundle;

// Delegated path: the model picks the tools, where TransactionGateway walks a known one (see ADR-0001)
public interface EvidenceGatherer {

    EvidenceBundle gather(Dispute dispute);
}
