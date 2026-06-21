package com.bino.dra.adapter.out.llm;

import java.util.List;

/**
 * Raised when the model's structured output breaks our invariants. Invalid output must never be
 * propagated as if valid — fail hard. A single repair round-trip may later precede this failure.
 */
public class OutputValidationException extends RuntimeException {

    private final List<String> violations;

    public OutputValidationException(List<String> violations) {
        super("Invalid LLM output: " + String.join(" | ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
