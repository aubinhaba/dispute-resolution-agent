package com.bino.dra.adapter.out.agent;

import java.util.List;

// Narrative only, no evidenceRefs on purpose: what the model says it consulted is not what it called
public record EvidenceDraft(
        String summary,
        List<String> findings
) {
}
