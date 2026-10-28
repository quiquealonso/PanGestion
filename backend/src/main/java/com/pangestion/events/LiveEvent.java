package com.pangestion.events;

import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.OrderType;

import java.util.List;

/**
 * Aviso en tiempo real para las pantallas de una empresa (cocina, sala, comandas).
 * Se publica como evento de Spring y LiveEventsHub lo envia por SSE cuando la transaccion se confirma.
 *
 * @param companyId empresa cuyas pantallas lo reciben
 * @param name      "change" (algo cambio: refrescar) o "ready" (productos listos para llevar a una mesa)
 * @param data      contenido en JSON (ChangeData o ReadyData)
 */
public record LiveEvent(Long companyId, String name, Object data) {

    /** Que pantallas deben refrescarse: "kitchen", "floor", "orders" o "session:{id}". */
    public record ChangeData(List<String> scopes) {
    }

    /** Productos que cocina o barra acaba de dejar listos para una mesa. */
    public record ReadyData(Long sessionId, String tableLabel, String waiterName, List<String> items) {
    }

    /** Pedido a domicilio que acaba de quedar listo: aviso a los repartidores. */
    public record DeliveryData(Long orderId, String customerName, String address, String time) {
    }

    public static LiveEvent change(Long companyId, String... scopes) {
        return new LiveEvent(companyId, "change", new ChangeData(List.of(scopes)));
    }

    public static LiveEvent ready(Long companyId, ReadyData data) {
        return new LiveEvent(companyId, "ready", data);
    }

    /** Aviso a los repartidores de un pedido a domicilio listo para llevar. */
    public static LiveEvent delivery(Order order) {
        return new LiveEvent(order.getCompany().getId(), "delivery", new DeliveryData(order.getId(),
                order.getCustomer().getName(), order.getDeliveryAddress(), order.getPickupTime().toString()));
    }

    /** Si el pedido es a domicilio y acaba de pasar a LISTO, el aviso para los repartidores. */
    public static java.util.Optional<LiveEvent> deliveryReady(Order order, OrderStatus before) {
        return order.getType() == OrderType.DOMICILIO && order.getStatus() == OrderStatus.LISTO && before != OrderStatus.LISTO
                ? java.util.Optional.of(delivery(order))
                : java.util.Optional.empty();
    }
}
