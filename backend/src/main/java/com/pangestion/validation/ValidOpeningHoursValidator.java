package com.pangestion.validation;

import com.pangestion.dto.CompanyDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Comprueba {@link ValidOpeningHours} sobre los datos de una empresa. */
public class ValidOpeningHoursValidator implements ConstraintValidator<ValidOpeningHours, CompanyDto.Request> {

    @Override
    public boolean isValid(CompanyDto.Request request, ConstraintValidatorContext context) {
        if (request == null || request.openingTime() == null || request.closingTime() == null) {
            return true; // de los nulos ya se encarga @NotNull
        }
        if (request.openingTime().isBefore(request.closingTime())) {
            return true;
        }
        // El error se asocia a closingTime para que salga en fieldErrors como cualquier otro campo
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("closingTime")
                .addConstraintViolation();
        return false;
    }
}
