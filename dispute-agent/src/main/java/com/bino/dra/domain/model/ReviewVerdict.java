package com.bino.dra.domain.model;

import java.util.List;
import java.util.Objects;

// Three outcomes not two: an unusable review filed under "coherent" would fail open (see ADR-0024)
public sealed interface ReviewVerdict {

    record Coherent() implements ReviewVerdict {
    }

    record Incoherent(List<String> objections) implements ReviewVerdict {
        public Incoherent {
            objections = List.copyOf(objections);
            if (objections.isEmpty()) {
                throw new IllegalArgumentException("an incoherence without objections cannot be revised");
            }
        }
    }

    record Inconclusive(String reason) implements ReviewVerdict {
        public Inconclusive {
            Objects.requireNonNull(reason, "reason required");
        }
    }
}
