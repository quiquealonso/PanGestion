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
        List<OrderDto.Response> latestOrders,
        List<TopProduct> topProducts,
        List<IngredientDto.Response> lowStockIngredients) {

    public record TopProduct(String name, long units) {
    }
}
