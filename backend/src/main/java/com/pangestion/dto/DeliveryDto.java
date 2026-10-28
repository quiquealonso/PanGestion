package com.pangestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pangestion.model.LineExtra;
import com.pangestion.model.Order;
import com.pangestion.model.OrderLine;
import com.pangestion.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Pantalla del repartidor: pedidos a domicilio de hoy. */
public final class DeliveryDto {

    private DeliveryDto() {
    }

    /**
     * Un pedido a domicilio. items = "2× Barbacoa (con huevo, sin cebolla)". mine = lo lleva quien lo consulta.
     * pickupTime es la hora a la que el cliente quiere recibirlo.
     */
    public record Item(Long id, OrderStatus status, String customerName, String customerPhone, String address,
                       LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time, String notes, BigDecimal total,
                       int itemCount, List<String> items, String driverName, boolean mine, LocalDateTime dispatchedAt) {

        public static Item from(Order order, String currentEmail) {
            var driver = order.getDriver();
            return new Item(order.getId(), order.getStatus(), order.getCustomer().getName(), order.getCustomer().getPhone(),
                    order.getDeliveryAddress(), order.getPickupDate(), order.getPickupTime(), order.getNotes(),
                    order.getTotal(), order.getItemCount(), order.getLines().stream().map(Item::describe).toList(),
                    driver == null ? null : driver.getName(),
                    driver != null && driver.getEmail().equalsIgnoreCase(currentEmail), order.getDispatchedAt());
        }

        private static String describe(OrderLine line) {
            List<String> custom = new ArrayList<>();
            LineExtra.names(line.getExtras()).forEach(e -> custom.add("con " + e.toLowerCase()));
            line.getRemovedIngredients().forEach(r -> custom.add("sin " + r.toLowerCase()));
            return line.getQuantity() + "× " + line.getProduct().getName()
                    + (custom.isEmpty() ? "" : " (" + String.join(", ", custom) + ")");
        }
    }
}
