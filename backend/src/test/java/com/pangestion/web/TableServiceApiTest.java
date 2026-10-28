package com.pangestion.web;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.model.Ingredient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Servicio de mesas (camareros): /api/service. */
@DisplayName("API · Servicio de mesas")
class TableServiceApiTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DiningAreaRepository areaRepository;

    private Company bar;
    private User waiter;
    private DiningTable one;
    private DiningTable two;
    private DiningTable three;
    private Product burger;
    private Ingredient onion;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        bar.setType(BusinessType.BAR);
        waiter = user(bar, Role.CAMARERO);
        DiningArea sala = new DiningArea();
        sala.setCompany(bar);
        sala.setName("Sala");
        sala.setWidth(20);
        sala.setHeight(10);
        one = table(sala, "1", 0);
        two = table(sala, "2", 3);
        three = table(sala, "3", 6);
        areaRepository.save(sala);
        onion = ingredient(bar, "Cebolla", null);
        Category food = category(bar, "Comida");
        burger = product(food, "Hamburguesa", "9.50", onion);
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

    private long open(int guests, DiningTable... tables) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (DiningTable t : tables) ids.append(ids.isEmpty() ? "" : ", ").append(t.getId());
        String json = mvc.perform(postJson("/api/service/sessions", """
                        {"tableIds": [%s], "guests": %d, "notes": "Cumpleaños"}
                        """.formatted(ids, guests)).with(as(waiter)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("id").asLong();
    }

    private String round(String lines) {
        return "{\"lines\": [" + lines + "]}";
    }

    private String line(int quantity) {
        return """
                {"productId": %d, "quantity": %d, "notes": "poco hecha", "removedIngredientIds": [%d]}
                """.formatted(burger.getId(), quantity, onion.getId());
    }

    @Test
    @DisplayName("el camarero abre la mesa: 201; el plano la marca ocupada y queda su nombre")
    void openTable() throws Exception {
        mvc.perform(postJson("/api/service/sessions", """
                        {"tableIds": [%d], "guests": 3, "notes": null}
                        """.formatted(one.getId())).with(as(waiter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableLabel").value("1"))
                .andExpect(jsonPath("$.guests").value(3))
                .andExpect(jsonPath("$.waiterName").value(waiter.getName()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mvc.perform(getJson("/api/service/floor").with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tables[0].session.guests").value(3))
                .andExpect(jsonPath("$[0].tables[1].session").doesNotExist());
    }

    @Test
    @DisplayName("una mesa ocupada no se puede abrir otra vez: 409")
    void tableAlreadyOccupied() throws Exception {
        open(2, one);
        mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%d, %d], \"guests\": 4}".formatted(two.getId(), one.getId()))
                        .with(as(waiter)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La mesa 1 ya esta ocupada"));
    }

    @Test
    @DisplayName("juntar mesas y luego cambiarse a otra: las anteriores quedan libres")
    void joinAndMoveTables() throws Exception {
        long session = open(6, one);
        mvc.perform(putJson("/api/service/sessions/{id}/tables", "{\"tableIds\": [%d, %d]}".formatted(two.getId(), one.getId()), session)
                        .with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableLabel").value("1 + 2"))
                .andExpect(jsonPath("$.tables.length()").value(2));

        mvc.perform(putJson("/api/service/sessions/{id}/tables", "{\"tableIds\": [%d]}".formatted(three.getId()), session).with(as(waiter)))
                .andExpect(jsonPath("$.tableLabel").value("3"));
        open(2, one); // la 1 vuelve a estar libre
    }

    @Test
    @DisplayName("rondas: cada producto con su ronda, precio, notas e ingredientes quitados; total acumulado")
    void rounds() throws Exception {
        long session = open(2, one);
        mvc.perform(postJson("/api/service/sessions/{id}/rounds", round(line(1) + "," + line(2)), session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds").value(1))
                .andExpect(jsonPath("$.lines[0].removedIngredients[0]").value("Cebolla"))
                .andExpect(jsonPath("$.lines[0].notes").value("poco hecha"))
                .andExpect(jsonPath("$.total").value(28.5));

        mvc.perform(postJson("/api/service/sessions/{id}/rounds", round(line(1)), session).with(as(waiter)))
                .andExpect(jsonPath("$.rounds").value(2))
                .andExpect(jsonPath("$.lines[2].round").value(2))
                .andExpect(jsonPath("$.itemCount").value(4));
    }

    @Test
    @DisplayName("los comensales se cambian cuando haga falta (sin nada cobrado): no dependen de los productos")
    void changeGuests() throws Exception {
        long session = open(4, one);
        mvc.perform(postJson("/api/service/sessions/{id}/rounds", round(line(2)), session).with(as(waiter)))
                .andExpect(status().isOk());
        mvc.perform(putJson("/api/service/sessions/{id}", "{\"guests\": 1, \"notes\": \"Trona\"}", session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests").value(1))
                .andExpect(jsonPath("$.notes").value("Trona"));
    }

    @Test
    @DisplayName("quitar una línea; liberar sin cobrar: 409; cobrada se libera y ya no se puede pedir: 409")
    void removeLineAndClose() throws Exception {
        long session = open(2, one);
        String json = mvc.perform(postJson("/api/service/sessions/{id}/rounds", round(line(1) + "," + line(1)), session).with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        long first = JSON.readTree(json).get("lines").get(0).get("id").asLong();

        mvc.perform(delete("/api/service/sessions/{id}/lines/{lineId}", session, first).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));

        // Sin cobrar no se puede liberar; cobrada, si
        mvc.perform(post("/api/service/sessions/{id}/close", session).with(as(waiter)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Quedan 9,50 € por cobrar en la mesa 1"));
        mvc.perform(postJson("/api/service/sessions/{id}/payments",
                        "{\"amount\": 9.50, \"method\": \"TARJETA\", \"cashGiven\": null, \"guest\": null, \"label\": \"Total\", \"closeWhenPaid\": false}",
                        session).with(as(waiter)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/service/sessions/{id}/close", session).with(as(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.tableLabel").value("1"))
                .andExpect(jsonPath("$.tables.length()").value(0));
        mvc.perform(postJson("/api/service/sessions/{id}/rounds", round(line(1)), session).with(as(waiter)))
                .andExpect(status().isConflict());
        open(2, one);
    }

    @Test
    @DisplayName("no se puede borrar del plano una mesa ocupada: 409")
    void cannotDeleteOccupiedTable() throws Exception {
        open(2, one);
        User admin = user(bar, Role.ADMIN);
        mvc.perform(delete("/api/dining/tables/{id}", one.getId()).with(as(admin))).andExpect(status().isConflict());
        mvc.perform(delete("/api/dining/areas/{id}", one.getArea().getId()).with(as(admin))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("permisos: el camarero no edita el catálogo (403); el visor no abre mesas (403)")
    void permissions() throws Exception {
        mvc.perform(postJson("/api/categories", "{\"name\": \"X\", \"color\": \"#000000\", \"description\": null}").with(as(waiter)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%d], \"guests\": 2}".formatted(one.getId()))
                        .with(as(user(bar, Role.VISOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("una panadería no puede tener camareros: 409")
    void waiterOnlyInDining() throws Exception {
        Company forn = company("Forn");
        User admin = user(forn, Role.ADMIN);
        mvc.perform(postJson("/api/users", """
                        {"name": "Pep", "email": "pep@forn.es", "password": "secreto123", "role": "CAMARERO", "active": true, "companyId": null}
                        """).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Los roles camarero y cocina solo existen en bares y restaurantes"));
    }

    @Test
    @DisplayName("servicio de otra empresa: 404")
    void otherCompany() throws Exception {
        long session = open(2, one);
        Company other = company("Otro");
        other.setType(BusinessType.BAR);
        mvc.perform(getJson("/api/service/sessions/{id}", session).with(as(user(other, Role.CAMARERO))))
                .andExpect(status().isNotFound());
        assertThat(session).isPositive();
    }
}
