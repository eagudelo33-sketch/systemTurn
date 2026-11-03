package com.systemturn.turnos.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.util.function.BooleanSupplier;

/**
 * Gate for the PostgreSQL suite. Docker present: run. Docker absent and {@code -DrequireDocker=true}: fail loudly
 * (a CI run must never go green by skipping). Docker absent otherwise: skip. The probe and the flag are injectable so
 * the three outcomes are unit-tested without a daemon.
 */
public final class DockerOrRequiredCondition implements ExecutionCondition {

    static final String REQUIRE_DOCKER = "requireDocker";

    private final BooleanSupplier dockerAvailable;
    private final boolean required;

    /** Used by JUnit through {@link EnabledIfDockerOrRequired}: real Docker probe and the system property. */
    public DockerOrRequiredCondition() {
        this(() -> DockerClientFactory.instance().isDockerAvailable(), isRequired(System.getProperty(REQUIRE_DOCKER)));
    }

    DockerOrRequiredCondition(BooleanSupplier dockerAvailable, boolean required) {
        this.dockerAvailable = dockerAvailable;
        this.required = required;
    }

    static boolean isRequired(String value) {
        return "true".equalsIgnoreCase(value);
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return evaluate();
    }

    ConditionEvaluationResult evaluate() {
        if (dockerAvailable.getAsBoolean()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        if (required) {
            throw new IllegalStateException(
                    "Docker is not available but -D" + REQUIRE_DOCKER + "=true: the PostgreSQL suite must run, not be skipped");
        }
        return ConditionEvaluationResult.disabled(
                "Docker is not available: PostgreSQL suite skipped (set -D" + REQUIRE_DOCKER + "=true to fail instead)");
    }
}
