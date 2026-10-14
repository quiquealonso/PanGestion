package com.pangestion.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Formato comun de los errores que devuelve la API. */
public record ErrorResponse(int status, String message, Map<String, String> fieldErrors, LocalDateTime timestamp) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Map.of(), LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, message, fieldErrors, LocalDateTime.now());
    }
}
