package com.bino.dra.application.port.out;

// Application-layer type: the orchestrator recognises an outage without knowing Spring AI or the
// Anthropic SDK. A known outage becomes a motivated ESCALATE; a bug stays an exception (ADR-0022)
public class DependencyUnavailableException extends RuntimeException {

    private final String dependency;

    public DependencyUnavailableException(String dependency, String detail, Throwable cause) {
        super("dependency unavailable: " + dependency + " - " + detail, cause);
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
