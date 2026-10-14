package com.pangestion.exception;

/** Recurso que no existe: se traduce a HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Long id) {
        super(resource + " con id " + id + " no encontrado");
    }
}
