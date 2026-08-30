package com.bino.dra.adapter.out.agent;

import java.util.List;

public record EvidenceDraft(
        String summary,
        List<String> findings
) {
}
