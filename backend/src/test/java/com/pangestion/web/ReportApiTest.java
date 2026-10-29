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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Informe de sala: /api/reports/dining. */
@DisplayName("API · Informe de sala")
class ReportApiTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DiningAreaRepository areaRepository;

    private Company bar;
    private User waiter;
    private User cook;
    private User manager;
    private DiningTable one;
    private DiningTable two;
    private Product burger;
    private Product beer;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        bar.setType(BusinessType.BAR);
        waiter = user(bar, Role.CAMARERO);
        cook = user(bar, Role.COCINA);
        manager = user(bar, Role.GESTOR);
        DiningArea sala = new DiningArea();
        sala.setCompany(bar);
        sala.setName("Sala");
        sala.setWidth(10);
        sala.setHeight(10);
        one = table(sala, "1", 0);
        two = table(sala, "2", 3);
        areaRepository.save(sala);
        Category food = category(bar, "Comida");
        burger = product(food, "Hamburguesa", "10.00");
        beer = product(food, "Caña", "2.00");
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

    /** Abre la mesa, pide, cocina deja listas las hamburguesas, se cobra con tarjeta y se libera. */
    private void fullService(DiningTable table, int guests, int burgers, int beers) throws Exception {
        String json = mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%d], \"guests\": %d}".formatted(table.getId(), guests))
                        .with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        long session = JSON.readTree(json).get("id").asLong();
        String round = burgers > 0
                ? "{\"lines\": [{\"productId\": %d, \"quantity\": %d}, {\"productId\": %d, \"quantity\": %d}]}"
                        .formatted(burger.getId(), burgers, beer.getId(), beers)
                : "{\"lines\": [{\"productId\": %d, \"quantity\": %d}]}".formatted(beer.getId(), beers);
        json = mvc.perform(postJson("/api/service/sessions/{id}/rounds", round, session).with(as(waiter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        if (burgers > 0) {
            long burgerLine = JSON.readTree(json).get("lines").get(0).get("id").asLong();
            for (String status : new String[] {"PREPARANDO", "LISTO"}) {
                mvc.perform(patchJson("/api/kitchen/items", "{\"items\": [{\"source\": \"MESA\", \"id\": %d}], \"status\": \"%s\"}"
                        .formatted(burgerLine, status)).with(as(cook))).andExpect(status().isNoContent());
            }
        }
        String total = JSON.readTree(json).get("total").asString();
        mvc.perform(postJson("/api/service/sessions/{id}/payments", """
                        {"amount": %s, "method": "TARJETA", "closeWhenPaid": true}
                        """.formatted(total), session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("resumen del día: cobrado, mesas, comensales, ticket medio, productos, camareros, mesas y cocina")
    void report() throws Exception {
        fullService(one, 2, 2, 2); // 24,00
        fullService(two, 4, 1, 3); // 16,00
        fullService(one, 1, 0, 1); //  2,00 (la mesa 1 dos veces)
        String today = LocalDate.now().toString();

        mvc.perform(getJson("/api/reports/dining?from=" + today + "&to=" + today).with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(42.0))
                .andExpect(jsonPath("$.card").value(42.0))
                .andExpect(jsonPath("$.tables").value(3))
                .andExpect(jsonPath("$.guests").value(7))
                .andExpect(jsonPath("$.avgTicket").value(14.0))
                .andExpect(jsonPath("$.avgPerGuest").value(6.0))
                .andExpect(jsonPath("$.byDay.length()").value(1))
                .andExpect(jsonPath("$.topProducts[0].name").value("Caña"))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(6))
                .andExpect(jsonPath("$.byWaiter[0].tables").value(3))
                .andExpect(jsonPath("$.byTable[0].table").value("1"))
                .andExpect(jsonPath("$.byTable[0].uses").value(2))
                .andExpect(jsonPath("$.byTable[0].revenue").value(26.0))
                .andExpect(jsonPath("$.kitchen[0].station").value("COCINA"))
                .andExpect(jsonPath("$.kitchen[0].items").value(2));
    }

    @Test
    @DisplayName("por defecto, los últimos 7 días (un día por fila, aunque esté vacío)")
    void defaultRange() throws Exception {
        mvc.perform(getJson("/api/reports/dining").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byDay.length()").value(7))
                .andExpect(jsonPath("$.tables").value(0))
                .andExpect(jsonPath("$.avgTicket").value(0));
    }

    @Test
    @DisplayName("fechas al revés o más de un año: 409; el camarero y la cocina no ven la facturación: 403")
    void rulesAndPermissions() throws Exception {
        mvc.perform(getJson("/api/reports/dining?from=2026-10-10&to=2026-10-01").with(as(manager)))
                .andExpect(status().isConflict());
        mvc.perform(getJson("/api/reports/dining?from=2025-01-01&to=2026-10-01").with(as(manager)))
                .andExpect(status().isConflict());
        mvc.perform(getJson("/api/reports/dining").with(as(waiter))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/reports/dining").with(as(cook))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/reports/dining").with(as(user(bar, Role.VISOR)))).andExpect(status().isOk());
    }
}
