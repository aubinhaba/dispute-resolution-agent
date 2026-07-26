package com.bino.dra.adapter.out.llm;

import java.util.List;

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
