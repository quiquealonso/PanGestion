package com.pangestion.web;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.Station;
import com.pangestion.model.TableShape;
import com.pangestion.model.User;
import com.pangestion.repository.DiningAreaRepository;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inicio de un bar o restaurante: /api/dashboard/dining. */
@DisplayName("API · Inicio de sala")
class DiningDashboardApiTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DiningAreaRepository areaRepository;

    private Company bar;
    private User waiter;
    private User cook;
    private DiningTable one;
    private DiningTable two;
    private DiningTable three;
    private Product burger;
    private Product beer;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        bar.setType(BusinessType.BAR);
        waiter = user(bar, Role.CAMARERO);
        cook = user(bar, Role.COCINA);
        DiningArea sala = new DiningArea();
        sala.setCompany(bar);
        sala.setName("Sala");
        sala.setWidth(12);
        sala.setHeight(6);
        one = table(sala, "1", 0);
        two = table(sala, "2", 3);
        three = table(sala, "3", 6);
        areaRepository.save(sala);
        Category food = category(bar, "Comida");
        burger = product(food, "Hamburguesa", "10.00");
        beer = product(food, "Caña", "2.00");
        beer.setStation(Station.BARRA);
    }

    private DiningTable table(DiningArea area, String name, int x) {
        DiningTable table = new DiningTable();
        table.setCompany(bar);
        table.setArea(area);
        table.setName(name);
        table.setSeats(4);
        table.setShape(TableShape.RECT);
        table.setX(x);
        table.setWidth(2);
        table.setHeight(2);
        area.getTables().add(table);
        return table;
    }

    private long open(String tableIds, int guests) throws Exception {
        String json = mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%s], \"guests\": %d}".formatted(tableIds, guests))
                        .with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("id").asLong();
    }

    @Test
    @DisplayName("sala ahora: mesas ocupadas (juntas cuentan dos), comensales, listos, colas de cocina y barra, para llevar y caja")
    void summary() throws Exception {
        long joined = open(one.getId() + ", " + two.getId(), 5);
        String json = mvc.perform(postJson("/api/service/sessions/{id}/rounds", """
                        {"lines": [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 3}]}
                        """.formatted(burger.getId(), beer.getId()), joined).with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        long burgerLine = JSON.readTree(json).get("lines").get(0).get("id").asLong();
        mvc.perform(patchJson("/api/kitchen/items", "{\"items\": [{\"source\": \"MESA\", \"id\": %d}], \"status\": \"PREPARANDO\"}"
                .formatted(burgerLine)).with(as(cook)));

        // Otra mesa, cobrada y cerrada hoy
        long paid = open(String.valueOf(three.getId()), 2);
        mvc.perform(postJson("/api/service/sessions/{id}/rounds", "{\"lines\": [{\"productId\": %d, \"quantity\": 1}]}"
                .formatted(burger.getId()), paid).with(as(waiter)));
        mvc.perform(postJson("/api/service/sessions/{id}/payments", "{\"amount\": 10, \"method\": \"EFECTIVO\", \"closeWhenPaid\": true}", paid)
                .with(as(waiter)));

        Customer luis = customer(bar, "Luis", "luis@correo.es");
        order(luis, LocalDate.now(), "21:00", OrderStatus.PENDIENTE, burger, 1);
        order(luis, LocalDate.now(), "21:30", OrderStatus.ENTREGADO, burger, 1);

        mvc.perform(getJson("/api/dashboard/dining").with(as(user(bar, Role.GESTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables").value(3))
                .andExpect(jsonPath("$.occupiedTables").value(2))
                .andExpect(jsonPath("$.openSessions").value(1))
                .andExpect(jsonPath("$.guestsNow").value(5))
                .andExpect(jsonPath("$.kitchenQueue").value(2))
                .andExpect(jsonPath("$.barQueue").value(3))
                .andExpect(jsonPath("$.readyItems").value(0))
                .andExpect(jsonPath("$.takeawayToday").value(1))
                .andExpect(jsonPath("$.revenueToday").value(10.0))
                .andExpect(jsonPath("$.cashToday").value(10.0))
                .andExpect(jsonPath("$.closedToday").value(1))
                .andExpect(jsonPath("$.avgTicketToday").value(10.0));
    }

    @Test
    @DisplayName("una panadería no tiene resumen de sala: 409")
    void bakery() throws Exception {
        mvc.perform(getJson("/api/dashboard/dining").with(as(user(company("Forn"), Role.ADMIN))))
                .andExpect(status().isConflict());
    }
}
