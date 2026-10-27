package com.pangestion.controller;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Cabecera Location de las respuestas 201 (S6 y S9): la URL del detalle del recurso recien creado.
 */
final class Locations {

    private Locations() {
    }

    /** Detalle bajo la ruta de la peticion: POST /api/categories -> /api/categories/{id}. */
    static URI child(Object id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }

    /** Detalle canonico en otra ruta: POST /api/customers/3/orders -> /api/orders/{id}. */
    static URI of(String path, Object id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).buildAndExpand(id).toUri();
    }
}
