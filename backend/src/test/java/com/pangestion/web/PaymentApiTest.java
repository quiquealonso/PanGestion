package com.pangestion.web;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.TableShape;
import com.pangestion.model.User;
import com.pangestion.repository.DiningAreaRepository;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cobro de mesas en la barra: /api/service/sessions/{id}/payments, /bill y /day. */
@DisplayName("API · Cobro de mesas")
class PaymentApiTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DiningAreaRepository areaRepository;

    private User waiter;
    private DiningTable table;
    private Product burger;
    private Product bravas;

    @BeforeEach
    void setUp() {
        Company bar = company("Bar");
        bar.setType(BusinessType.BAR);
        waiter = user(bar, Role.CAMARERO);
        DiningArea sala = new DiningArea();
        sala.setCompany(bar);
        sala.setName("Sala");
        sala.setWidth(10);
        sala.setHeight(10);
        table = new DiningTable();
        table.setCompany(bar);
        table.setArea(sala);
        table.setName("4");
        table.setSeats(4);
        table.setShape(TableShape.RECT);
        table.setWidth(2);
        table.setHeight(2);
        sala.getTables().add(table);
        areaRepository.save(sala);
        Category food = category(bar, "Comida");
        burger = product(food, "Hamburguesa", "10.00");
        bravas = product(food, "Bravas", "5.00");
    }

    /**
     * Mesa de 3: dos hamburguesas y unas bravas (los productos son de la mesa, no de un comensal).
     * Total 25,00; a partes iguales: C1 = 8,34 (se queda el centimo sobrante), C2 = 8,33, C3 = 8,33.
     */
    private long tableOfThree() throws Exception {
        String json = mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%d], \"guests\": 3}".formatted(table.getId()))
                        .with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        long session = JSON.readTree(json).get("id").asLong();
        String lines = """
                {"lines": [
                  {"productId": %d, "quantity": 1},
                  {"productId": %d, "quantity": 1},
                  {"productId": %d, "quantity": 1}
                ]}
                """.formatted(burger.getId(), burger.getId(), bravas.getId());
        mvc.perform(postJson("/api/service/sessions/{id}/rounds", lines, session).with(as(waiter))).andExpect(status().isOk());
        return session;
    }

    private static String payment(String amount, String method, String cashGiven, Integer guest, boolean close) {
        return """
                {"amount": %s, "method": "%s", "cashGiven": %s, "guest": %s, "label": "Parte", "closeWhenPaid": %s}
                """.formatted(amount, method, cashGiven, guest, close);
    }

    @Test
    @DisplayName("cuenta entre comensales: el total a partes iguales; suman el total")
    void bill() throws Exception {
        long session = tableOfThree();
        mvc.perform(getJson("/api/service/sessions/{id}/bill", session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.pending").value(25.0))
                .andExpect(jsonPath("$.guests[0].amount").value(8.34))
                .andExpect(jsonPath("$.guests[1].amount").value(8.33))
                .andExpect(jsonPath("$.guests[2].amount").value(8.33));
    }

    @Test
    @DisplayName("pago en efectivo con cambio; por comensal solo su parte exacta y una vez; al quedar pagada se libera")
    void payByGuest() throws Exception {
        long session = tableOfThree();

        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("10.00", "EFECTIVO", "20", 1, true), session).with(as(waiter)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Al comensal 1 le tocan 8,34 €"));

        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("8.34", "EFECTIVO", "20", 1, true), session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(8.34))
                .andExpect(jsonPath("$.pending").value(16.66))
                .andExpect(jsonPath("$.payments[0].change").value(11.66))
                .andExpect(jsonPath("$.payments[0].label").value("Comensal 1"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("8.34", "TARJETA", "null", 1, true), session).with(as(waiter)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El comensal 1 ya ha pagado"));

        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("8.33", "TARJETA", "null", 2, true), session).with(as(waiter)))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("8.33", "TARJETA", "null", 3, true), session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("con un comensal ya cobrado no se cambia el número de comensales: 409")
    void guestsFixedOncePaid() throws Exception {
        long session = tableOfThree();
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("8.34", "TARJETA", "null", 1, false), session).with(as(waiter)));
        mvc.perform(putJson("/api/service/sessions/{id}", "{\"guests\": 4, \"notes\": null}", session).with(as(waiter)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya ha pagado algun comensal su parte: no se puede cambiar el numero de comensales"));
        mvc.perform(putJson("/api/service/sessions/{id}", "{\"guests\": 3, \"notes\": \"Trona\"}", session).with(as(waiter)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("no se cobra más de lo pendiente ni con menos efectivo del importe: 409")
    void limits() throws Exception {
        long session = tableOfThree();
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("30", "TARJETA", "null", null, false), session).with(as(waiter)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Solo quedan 25,00 € por cobrar"));
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("25", "EFECTIVO", "20", null, false), session).with(as(waiter)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("0", "TARJETA", "null", null, false), session).with(as(waiter)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    @DisplayName("a partes iguales y anular un cobro; caja del día con efectivo, tarjeta y la mesa cerrada")
    void equalPartsAndDay() throws Exception {
        long session = tableOfThree();
        String json = mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("12.50", "EFECTIVO", "null", null, false), session)
                        .with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        JsonNode first = JSON.readTree(json).get("payments").get(0);

        mvc.perform(delete("/api/service/sessions/{id}/payments/{p}", session, first.get("id").asLong()).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(0));

        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("12.50", "EFECTIVO", "null", null, false), session).with(as(waiter)));
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("12.50", "TARJETA", "null", null, false), session).with(as(waiter)));
        mvc.perform(post("/api/service/sessions/{id}/close", session).with(as(waiter))).andExpect(status().isOk());

        mvc.perform(getJson("/api/service/day").with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(12.5))
                .andExpect(jsonPath("$.card").value(12.5))
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.tables").value(1))
                .andExpect(jsonPath("$.sessions[0].tableLabel").value("4"));
    }

    @Test
    @DisplayName("con algo cobrado no se puede anular un producto si el total quedaría por debajo: 409")
    void cannotRemoveBelowPaid() throws Exception {
        long session = tableOfThree();
        String json = mvc.perform(getJson("/api/service/sessions/{id}", session).with(as(waiter))).andReturn().getResponse().getContentAsString();
        long burgerLine = JSON.readTree(json).get("lines").get(0).get("id").asLong();
        mvc.perform(postJson("/api/service/sessions/{id}/payments", payment("20", "TARJETA", "null", null, false), session).with(as(waiter)));

        mvc.perform(delete("/api/service/sessions/{id}/lines/{line}", session, burgerLine).with(as(waiter)))
                .andExpect(status().isConflict());
    }
}
