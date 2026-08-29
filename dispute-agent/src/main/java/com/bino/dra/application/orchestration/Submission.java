package com.bino.dra.application.orchestration;

import com.bino.dra.domain.model.DisputeCase;

// `created` cannot be derived from the state: a replay during processing is PENDING either way
public record Submission(DisputeCase state, boolean created) {
}
