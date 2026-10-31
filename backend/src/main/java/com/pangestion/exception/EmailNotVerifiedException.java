package com.pangestion.exception;

/** La cuenta del cliente aun no ha confirmado su email: se traduce a HTTP 403. */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Confirma tu email: te enviamos un enlace al registrarte. Si no lo encuentras, pide otro.");
    }
}
