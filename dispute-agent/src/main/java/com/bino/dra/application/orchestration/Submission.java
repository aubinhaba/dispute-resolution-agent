package com.bino.dra.application.orchestration;

import com.bino.dra.domain.model.DisputeCase;

public record Submission(DisputeCase state, boolean created) {
}
