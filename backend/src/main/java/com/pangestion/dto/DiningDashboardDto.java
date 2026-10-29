package com.pangestion.dto;

import java.math.BigDecimal;

/**
 * Inicio de un bar o restaurante: como esta la sala ahora mismo y la caja de hoy.
 * Las colas de cocina y barra cuentan unidades pendientes o preparandose de las mesas abiertas.
 */
public record DiningDashboardDto(
        int tables,
        int occupiedTables,
        int openSessions,
        int guestsNow,
        /** Unidades listas para llevar a las mesas */
        int readyItems,
        int kitchenQueue,
        int barQueue,
        /** Pedidos para llevar de hoy que aun no se han entregado */
        long takeawayToday,
        BigDecimal revenueToday,
        BigDecimal cashToday,
        BigDecimal cardToday,
        int closedToday,
        BigDecimal avgTicketToday) {
}
