package com.pangestion.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validacion propia (S12): la hora de apertura tiene que ser anterior a la de cierre.
 * Se pone sobre la clase porque compara dos campos; el error se asigna al campo closingTime.
 */
@Documented
@Constraint(validatedBy = ValidOpeningHoursValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidOpeningHours {

    String message() default "debe ser posterior a la hora de apertura";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
