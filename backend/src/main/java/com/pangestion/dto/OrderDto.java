package com.pangestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pangestion.model.Order;
import com.pangestion.model.OrderLine;
import com.pangestion.model.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class OrderDto {

    private OrderDto() {
    }

    /** Alta o edicion de un pedido desde el panel de gestion. */
    public record Request(
            @NotNull Long customerId,
            @NotNull LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines) {
    }

    /** Creacion anidada POST /api/customers/{id}/orders: el cliente lo decide la ruta, no el cuerpo. */
    public record CustomerOrderRequest(
            @NotNull LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines) {
    }

    /** Pedido realizado por un cliente desde la parte publica, a la panaderia (empresa) que elige. */
    public record PublicRequest(
            @NotNull Long companyId,
            @NotNull @FutureOrPresent LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @NotNull @Valid CustomerDto.Request customer,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines) {
    }

    /** removedIngredientIds: ingredientes del producto que el cliente quiere quitar (opcional). */
    public record Line(@NotNull Long productId, @Min(1) @Max(999) int quantity, List<Long> removedIngredientIds) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    /**
     * Fila de la hoja de produccion: unidades de un producto (con su personalizacion) para una hora de recogida.
     * Ordenada por categoria, producto y hora.
     */
    public record ProductionRow(Long productId, String productName, String abbreviation,
                                String categoryName, String categoryColor, List<String> removedIngredients,
                                @JsonFormat(pattern = "HH:mm") LocalTime pickupTime, int quantity) {
    }

    public record LineResponse(Long productId, String productName, int quantity,
                               BigDecimal unitPrice, BigDecimal subtotal, List<String> removedIngredients) {

        static LineResponse from(OrderLine line) {
            return new LineResponse(line.getProduct().getId(), line.getProduct().getName(),
                    line.getQuantity(), line.getUnitPrice(), line.getSubtotal(), List.copyOf(line.getRemovedIngredients()));
        }
    }

    public record Response(Long id, Long customerId, String customerName, String customerEmail, String customerPhone,
                           Long companyId, String companyName, LocalDate pickupDate,
                           @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
                           OrderStatus status, String notes, BigDecimal total, int itemCount,
                           LocalDateTime createdAt, List<LineResponse> lines) {

        public static Response from(Order order) {
            var customer = order.getCustomer();
            return new Response(order.getId(), customer.getId(), customer.getName(), customer.getEmail(),
                    customer.getPhone(), order.getCompany().getId(), order.getCompany().getName(),
                    order.getPickupDate(), order.getPickupTime(), order.getStatus(), order.getNotes(),
                    order.getTotal(), order.getItemCount(), order.getCreatedAt(),
                    order.getLines().stream().map(LineResponse::from).toList());
        }
    }
}
