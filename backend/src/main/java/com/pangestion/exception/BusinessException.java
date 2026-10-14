package com.pangestion.exception;

/** Regla de negocio incumplida (duplicados, borrados no permitidos...): se traduce a HTTP 409. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
