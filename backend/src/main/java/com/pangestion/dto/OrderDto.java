package com.pangestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pangestion.model.Order;
import com.pangestion.model.OrderLine;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.OrderType;
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

    /**
     * Alta o edicion de un pedido desde el panel de gestion. type: RECOGER (por defecto) o DOMICILIO,
     * que necesita deliveryAddress y que el negocio haga envios. pickupTime es la hora de recogida o de entrega.
     */
    public record Request(
            @NotNull Long customerId,
            @NotNull LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines,
            OrderType type,
            @Size(max = 255) String deliveryAddress) {

        public Request(Long customerId, LocalDate pickupDate, LocalTime pickupTime, String notes, List<Line> lines) {
            this(customerId, pickupDate, pickupTime, notes, lines, null, null);
        }
    }

    /** Creacion anidada POST /api/customers/{id}/orders: el cliente lo decide la ruta, no el cuerpo. */
    public record CustomerOrderRequest(
            @NotNull LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines,
            OrderType type,
            @Size(max = 255) String deliveryAddress) {

        public CustomerOrderRequest(LocalDate pickupDate, LocalTime pickupTime, String notes, List<Line> lines) {
            this(pickupDate, pickupTime, notes, lines, null, null);
        }
    }

    /** Pedido realizado por un cliente desde la parte publica, a la panaderia (empresa) que elige. */
    public record PublicRequest(
            @NotNull Long companyId,
            @NotNull @FutureOrPresent LocalDate pickupDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
            @NotNull @Valid CustomerDto.Request customer,
            @Size(max = 500) String notes,
            @NotEmpty @Valid List<Line> lines,
            OrderType type,
            @Size(max = 255) String deliveryAddress) {
    }

    /**
     * removedIngredientIds: ingredientes del producto que el cliente quiere quitar (opcional).
     * addedIngredientIds: extras a anadir, con su precio (opcional).
     */
    public record Line(@NotNull Long productId, @Min(1) @Max(999) int quantity, List<Long> removedIngredientIds,
                       List<Long> addedIngredientIds) {

        public Line(Long productId, int quantity, List<Long> removedIngredientIds) {
            this(productId, quantity, removedIngredientIds, null);
        }
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    /**
     * Fila de la hoja de produccion: unidades de un producto (con su personalizacion) para una hora de recogida.
     * Ordenada por categoria, producto y hora.
     */
    public record ProductionRow(Long productId, String productName, String abbreviation,
                                String categoryName, String categoryColor, List<String> removedIngredients,
                                List<String> extras, @JsonFormat(pattern = "HH:mm") LocalTime pickupTime, int quantity) {
    }

    /** unitPrice ya incluye los extras. */
    public record LineResponse(Long productId, String productName, int quantity,
                               BigDecimal unitPrice, BigDecimal subtotal, List<String> removedIngredients,
                               List<IngredientDto.LineExtraResponse> extras) {

        static LineResponse from(OrderLine line) {
            return new LineResponse(line.getProduct().getId(), line.getProduct().getName(),
                    line.getQuantity(), line.getUnitPrice(), line.getSubtotal(), List.copyOf(line.getRemovedIngredients()),
                    IngredientDto.LineExtraResponse.from(line.getExtras()));
        }
    }

    public record Response(Long id, Long customerId, String customerName, String customerEmail, String customerPhone,
                           Long companyId, String companyName, LocalDate pickupDate,
                           @JsonFormat(pattern = "HH:mm") LocalTime pickupTime,
                           OrderStatus status, String notes, BigDecimal total, int itemCount,
                           LocalDateTime createdAt, List<LineResponse> lines,
                           OrderType type, String deliveryAddress, String driverName) {

        public static Response from(Order order) {
            var customer = order.getCustomer();
            return new Response(order.getId(), customer.getId(), customer.getName(), customer.getEmail(),
                    customer.getPhone(), order.getCompany().getId(), order.getCompany().getName(),
                    order.getPickupDate(), order.getPickupTime(), order.getStatus(), order.getNotes(),
                    order.getTotal(), order.getItemCount(), order.getCreatedAt(),
                    order.getLines().stream().map(LineResponse::from).toList(),
                    order.getType(), order.getDeliveryAddress(), order.getDriver() == null ? null : order.getDriver().getName());
        }
    }
}
