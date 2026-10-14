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
            @NotNull Long storeId,
            @NotNull LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines) {
    }

    /** Pedido realizado por un cliente desde la parte publica. */
    public record PublicRequest(
            @NotNull Long storeId,
            @NotNull @FutureOrPresent LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @NotNull @Valid CustomerDto.Request customer,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines) {
    }

    public record Line(@NotNull Long productId, @Min(1) @Max(999) int quantity) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    public record LineResponse(Long productId, String productName, int quantity,
                               BigDecimal unitPrice, BigDecimal subtotal) {

        static LineResponse from(OrderLine line) {
            return new LineResponse(line.getProduct().getId(), line.getProduct().getName(),
                    line.getQuantity(), line.getUnitPrice(), line.getSubtotal());
        }
    }

    public record Response(Long id, Long customerId, String customerName, String customerEmail, String customerPhone,
                           Long storeId, String storeName, LocalDate pickupDate,
                           @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
                           OrderStatus status, String notes, BigDecimal total, int itemCount,
                           LocalDateTime createdAt, List<LineResponse> lines) {

        public static Response from(Order order) {
            var customer = order.getCustomer();
            return new Response(order.getId(), customer.getId(), customer.getName(), customer.getEmail(),
                    customer.getPhone(), order.getStore().getId(), order.getStore().getName(),
                    order.getPickupDate(), order.getPickupTime(), order.getStatus(), order.getNotes(),
                    order.getTotal(), order.getItemCount(), order.getCreatedAt(),
                    order.getLines().stream().map(LineResponse::from).toList());
        }
    }
}
