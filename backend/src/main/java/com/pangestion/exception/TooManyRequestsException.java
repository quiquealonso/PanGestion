package com.pangestion.exception;

import lombok.Getter;

/** Demasiados intentos seguidos (login, pedidos publicos): se traduce a HTTP 429 con Retry-After. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Demasiados intentos. Vuelve a probar en " + (retryAfterSeconds >= 60 ? (retryAfterSeconds + 59) / 60 + " min" : retryAfterSeconds + " s"));
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
