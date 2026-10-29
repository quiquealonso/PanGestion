package com.pangestion.model;

/** Donde se prepara un producto: decide en que pantalla aparece al pedirlo. */
public enum Station {
    /** Pantalla de cocina (platos, bocadillos...). */
    COCINA,
    /** Pantalla de barra (bebidas, cafes...). */
    BARRA,
    /** No se prepara (pan, agua embotellada...): sale ya listo para llevar a la mesa. */
    NINGUNA
}
