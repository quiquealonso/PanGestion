package com.pangestion.web;

import com.pangestion.events.LiveEvent;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedidos a domicilio y reparto: tipo de pedido, /api/delivery y aviso a los repartidores. */
@DisplayName("API · Reparto a domicilio")
@RecordApplicationEvents
class DeliveryApiTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private ApplicationEvents events;

    private Company bar;
    private User gestor;
    private User rafa;
    private User toni;
    private Customer ana;
    private Product pizza;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        bar.setType(BusinessType.BAR);
        bar.setDelivery(true);
        bar.setOrdersPerSlot(50);
        companyRepository.save(bar);
        gestor = user(bar, Role.GESTOR);
        rafa = user(bar, Role.REPARTIDOR);
        toni = user(bar, Role.REPARTIDOR);
        ana = customer(bar, "Ana", "ana@correo.es");
        pizza = product(category(bar, "Pizzas"), "Barbacoa", "8.50");
    }

    private String body(String type, String address) {
        return """
                {"customerId": %d, "pickupDate": "%s", "pickupTime": "13:00", "notes": null,
                 "lines": [{"productId": %d, "quantity": 1}], "type": %s, "deliveryAddress": %s}
                """.formatted(ana.getId(), TOMORROW, pizza.getId(),
                type == null ? "null" : "\"" + type + "\"", address == null ? "null" : "\"" + address + "\"");
    }

    /** Crea un pedido a domicilio y lo pasa a hoy (el reparto es de hoy; asi no depende de la hora a la que corre el test). */
    private long homeOrder() throws Exception {
        String json = mvc.perform(postJson("/api/orders", body("DOMICILIO", "C/ Mayor 12")).with(as(gestor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(json).get("id").asLong();
        var order = orderRepository.findById(id).orElseThrow();
        order.setPickupDate(LocalDate.now());
        orderRepository.save(order);
        return id;
    }

    private void setStatus(long id, String status) throws Exception {
        mvc.perform(patchJson("/api/orders/{id}/status", "{\"status\": \"%s\"}".formatted(status), id).with(as(gestor)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("tipo de pedido: por defecto para recoger; a domicilio con dirección; sin dirección o si el negocio no reparte: 409")
    void orderType() throws Exception {
        mvc.perform(postJson("/api/orders", body(null, null)).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RECOGER"))
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());
        mvc.perform(postJson("/api/orders", body("DOMICILIO", "C/ Mayor 12")).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DOMICILIO"))
                .andExpect(jsonPath("$.deliveryAddress").value("C/ Mayor 12"));
        mvc.perform(postJson("/api/orders", body("DOMICILIO", " ")).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Falta la direccion de entrega"));

        bar.setDelivery(false);
        companyRepository.save(bar);
        mvc.perform(postJson("/api/orders", body("DOMICILIO", "C/ Mayor 12")).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bar no hace envios a domicilio"));
    }

    @Test
    @DisplayName("reparto: al quedar listo se avisa a los repartidores; uno se lo lleva (transportando, a su nombre) y lo entrega")
    void takeAndDeliver() throws Exception {
        long id = homeOrder();
        mvc.perform(postJson("/api/delivery/{id}/take", "", id).with(as(rafa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El pedido #" + id + " no esta listo para llevar"));

        setStatus(id, "LISTO");
        var notices = events.stream(LiveEvent.class).filter(e -> e.name().equals("delivery")).toList();
        assertThat(notices).hasSize(1);
        assertThat(((LiveEvent.DeliveryData) notices.getFirst().data()).address()).isEqualTo("C/ Mayor 12");

        mvc.perform(getJson("/api/delivery").with(as(rafa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("LISTO"))
                .andExpect(jsonPath("$[0].address").value("C/ Mayor 12"))
                .andExpect(jsonPath("$[0].items[0]").value("1× Barbacoa"));

        mvc.perform(postJson("/api/delivery/{id}/take", "", id).with(as(rafa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSPORTANDO"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.driverName").value(rafa.getName()));
        mvc.perform(postJson("/api/delivery/{id}/take", "", id).with(as(toni)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El pedido #" + id + " ya lo lleva " + rafa.getName()));
        mvc.perform(postJson("/api/delivery/{id}/deliver", "", id).with(as(toni)))
                .andExpect(status().isConflict());

        mvc.perform(getJson("/api/orders/{id}", id).with(as(gestor)))
                .andExpect(jsonPath("$.status").value("TRANSPORTANDO"))
                .andExpect(jsonPath("$.driverName").value(rafa.getName()));
        mvc.perform(postJson("/api/delivery/{id}/deliver", "", id).with(as(rafa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGADO"));
    }

    @Test
    @DisplayName("no me lo llevo: vuelve a listo sin repartidor y lo puede coger otro")
    void release() throws Exception {
        long id = homeOrder();
        setStatus(id, "LISTO");
        mvc.perform(postJson("/api/delivery/{id}/take", "", id).with(as(rafa))).andExpect(status().isOk());
        mvc.perform(postJson("/api/delivery/{id}/release", "", id).with(as(rafa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LISTO"))
                .andExpect(jsonPath("$.driverName").doesNotExist());
        mvc.perform(postJson("/api/delivery/{id}/take", "", id).with(as(toni)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").value(toni.getName()));
    }

    @Test
    @DisplayName("un pedido para recoger no se reparte ni pasa a transportando: 409")
    void pickupIsNotDelivered() throws Exception {
        String json = mvc.perform(postJson("/api/orders", body(null, null)).with(as(gestor)))
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(json).get("id").asLong();
        mvc.perform(patchJson("/api/orders/{id}/status", "{\"status\": \"TRANSPORTANDO\"}", id).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Solo los pedidos a domicilio se transportan"));
        setStatus(id, "LISTO");
        mvc.perform(postJson("/api/delivery/{id}/take", "", id).with(as(rafa)))
                .andExpect(status().isConflict());
        assertThat(events.stream(LiveEvent.class).filter(e -> e.name().equals("delivery"))).isEmpty();
    }

    @Test
    @DisplayName("cuando cocina deja listo el último producto de un pedido a domicilio, también se avisa a los repartidores")
    void kitchenReadyNotifies() throws Exception {
        long id = homeOrder();
        long lineId = orderRepository.findById(id).orElseThrow().getLines().getFirst().getId();
        User cook = user(bar, Role.COCINA);
        mvc.perform(patchJson("/api/kitchen/items", "{\"items\": [{\"source\": \"LLEVAR\", \"id\": %d}], \"status\": \"PREPARANDO\"}"
                .formatted(lineId)).with(as(cook)));
        assertThat(events.stream(LiveEvent.class).filter(e -> e.name().equals("delivery"))).isEmpty();
        mvc.perform(patchJson("/api/kitchen/items", "{\"items\": [{\"source\": \"LLEVAR\", \"id\": %d}], \"status\": \"LISTO\"}"
                .formatted(lineId)).with(as(cook)));
        assertThat(events.stream(LiveEvent.class).filter(e -> e.name().equals("delivery"))).hasSize(1);
    }

    @Test
    @DisplayName("permisos: el repartidor solo usa el reparto (pedidos o productos: 403); un camarero no reparte (403)")
    void permissions() throws Exception {
        mvc.perform(getJson("/api/orders").with(as(rafa))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/products").with(as(rafa))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/auth/me").with(as(rafa))).andExpect(status().isOk());
        mvc.perform(getJson("/api/delivery").with(as(user(bar, Role.CAMARERO)))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/delivery").with(as(gestor))).andExpect(status().isOk());
    }
}
