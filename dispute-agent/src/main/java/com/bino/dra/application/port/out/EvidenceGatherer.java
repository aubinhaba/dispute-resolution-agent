package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.EvidenceBundle;

// Delegated investigation: the model picks the tools and the order, rather than Java walking a known path
public interface EvidenceGatherer {

    EvidenceBundle gather(Dispute dispute);
}
