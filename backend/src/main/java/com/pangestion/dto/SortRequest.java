package com.pangestion.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Nuevo orden de una lista: los ids en la posicion deseada (el primero queda en la posicion 0). */
public record SortRequest(@NotEmpty List<Long> ids) {
}
