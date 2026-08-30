package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.EvidenceBundle;

public interface EvidenceGatherer {

    EvidenceBundle gather(Dispute dispute);
}
