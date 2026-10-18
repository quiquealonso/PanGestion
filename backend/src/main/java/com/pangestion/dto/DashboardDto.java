package com.pangestion.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resumen de actividad para la pantalla de inicio. */
public record DashboardDto(
        long pendingOrders,
        long inProcessOrders,
        long readyOrders,
        long todayOrders,
        long customers,
        long activeProducts,
        BigDecimal monthRevenue,
        long monthOrders,
        BigDecimal averageTicket,
        List<TopProduct> topProducts) {

    public record TopProduct(String name, long units) {
    }

    /**
     * Unidades vendidas por dia (lunes a domingo) de una semana y de la anterior, para compararlas.
     * current[0] = lunes de weekStart ... current[6] = domingo.
     */
    public record WeeklySales(java.time.LocalDate weekStart, List<Long> current, List<Long> previous) {
    }
}
