package com.systemturn.turnos.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a PostgreSQL (Testcontainers) test class; see {@link DockerOrRequiredCondition}. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerOrRequiredCondition.class)
public @interface EnabledIfDockerOrRequired {
}
