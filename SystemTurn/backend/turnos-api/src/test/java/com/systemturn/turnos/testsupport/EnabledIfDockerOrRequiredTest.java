package com.systemturn.turnos.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec "PostgreSQL test suite gating". The Docker probe and the {@code requireDocker} flag are injected, so each
 * scenario is exercised without touching a real Docker daemon.
 */
class EnabledIfDockerOrRequiredTest {

    private static ConditionEvaluationResult evaluar(boolean dockerDisponible, boolean requerido) {
        return new DockerOrRequiredCondition(() -> dockerDisponible, requerido).evaluate();
    }

    @Test
    void skippedWithoutDockerWhenRequireDockerIsUnset() {
        ConditionEvaluationResult resultado = evaluar(false, false);

        assertThat(resultado.isDisabled()).isTrue();
        assertThat(resultado.getReason()).hasValueSatisfying(r -> assertThat(r).contains("Docker").contains("skipped"));
    }

    @Test
    void failsWhenDockerIsRequiredButUnavailableAndTheMessageMentionsRequireDocker() {
        assertThatThrownBy(() -> evaluar(false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requireDocker");
    }

    @Test
    void runsWhenDockerIsAvailableWhetherOrNotItIsRequired() {
        assertThat(evaluar(true, false).isDisabled()).isFalse();
        assertThat(evaluar(true, true).isDisabled()).isFalse();
    }

    @Test
    void dockerIsProbedExactlyOncePerEvaluation() {
        int[] consultas = {0};
        new DockerOrRequiredCondition(() -> {
            consultas[0]++;
            return true;
        }, false).evaluate();

        assertThat(consultas[0]).isEqualTo(1);
    }

    @Test
    void onlyTheLiteralTrueEnablesTheRequireDockerFlag() {
        assertThat(DockerOrRequiredCondition.isRequired("true")).isTrue();
        assertThat(DockerOrRequiredCondition.isRequired("TRUE")).isTrue();
        assertThat(DockerOrRequiredCondition.isRequired("false")).isFalse();
        assertThat(DockerOrRequiredCondition.isRequired("")).isFalse();
        assertThat(DockerOrRequiredCondition.isRequired("${requireDocker}")).isFalse();
        assertThat(DockerOrRequiredCondition.isRequired(null)).isFalse();
    }
}
