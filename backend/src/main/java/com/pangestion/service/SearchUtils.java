package com.pangestion.service;

/** Utilidades para las busquedas por texto. */
final class SearchUtils {

    private SearchUtils() {
    }

    /** Convierte el texto buscado en un patron LIKE en minusculas: "Pan" -> "%pan%". */
    static String likePattern(String search) {
        return "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";
    }
}
