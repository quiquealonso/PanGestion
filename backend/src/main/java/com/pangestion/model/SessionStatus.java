package com.pangestion.model;

/** Estado del servicio de una mesa. */
public enum SessionStatus {
    /** Mesa ocupada: se pueden pedir productos. */
    OPEN,
    /** Mesa liberada (los clientes se han ido). */
    CLOSED
}
