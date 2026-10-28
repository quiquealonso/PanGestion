package com.pangestion.web;

import com.pangestion.events.LiveEvent;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.model.Order;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantalla de cocina y barra: /api/kitchen. */
@DisplayName("API · Cocina y barra")
@RecordApplicationEvents
class KitchenApiTest extends ApiTestSupport {

    @Autowired
    private ApplicationEvents events;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DiningAreaRepository areaRepository;

    private Company bar;
    private User waiter;
    private User cook;
    private DiningTable table;
    private Product burger;
    private Product beer;
    private Product bread;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        bar.setType(BusinessType.BAR);
        waiter = user(bar, Role.CAMARERO);
        cook = user(bar, Role.COCINA);
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
        burger = product(food, "Hamburguesa", "9.50");
        beer = product(food, "Caña", "1.80");
        beer.setStation(Station.BARRA);
        bread = product(food, "Pan", "1.00");
        bread.setStation(Station.NINGUNA);
    }

    /** Abre la mesa y envia una ronda; devuelve la respuesta del servicio. */
    private JsonNode tableWithRound(String lines) throws Exception {
        String json = mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%d], \"guests\": 2}".formatted(table.getId()))
                        .with(as(waiter)))
                .andReturn().getResponse().getContentAsString();
        long session = JSON.readTree(json).get("id").asLong();
        json = mvc.perform(postJson("/api/service/sessions/{id}/rounds", "{\"lines\": [" + lines + "]}", session).with(as(waiter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json);
    }

    private static String line(Product product) {
        return "{\"productId\": %d, \"quantity\": 1}".formatted(product.getId());
    }

    private String move(String source, long id, String status) {
        return "{\"items\": [{\"source\": \"%s\", \"id\": %d}], \"status\": \"%s\"}".formatted(source, id, status);
    }

    @Test
    @DisplayName("cada estación ve lo suyo: la hamburguesa en cocina, la caña en barra, el pan en ninguna (ya listo)")
    void stations() throws Exception {
        JsonNode session = tableWithRound(line(burger) + "," + line(beer) + "," + line(bread));
        assertThat(session.get("lines").get(2).get("status").asString()).isEqualTo("LISTO");

        mvc.perform(getJson("/api/kitchen?station=COCINA").with(as(cook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Mesa 4"))
                .andExpect(jsonPath("$[0].source").value("MESA"))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].productName").value("Hamburguesa"));
        mvc.perform(getJson("/api/kitchen?station=BARRA").with(as(cook)))
                .andExpect(jsonPath("$[0].items[0].productName").value("Caña"));
    }

    @Test
    @DisplayName("cocina avanza un producto paso a paso; saltarse un paso: 409")
    void advance() throws Exception {
        long id = tableWithRound(line(burger)).get("lines").get(0).get("id").asLong();

        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "LISTO")).with(as(cook)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hamburguesa esta pendiente: no puede pasar a listo"));
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "PREPARANDO")).with(as(cook))).andExpect(status().isNoContent());
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "LISTO")).with(as(cook))).andExpect(status().isNoContent());

        // Aviso al camarero: mesa y productos listos
        var ready = events.stream(LiveEvent.class).filter(e -> e.name().equals("ready")).toList();
        assertThat(ready).hasSize(1);
        var data = (LiveEvent.ReadyData) ready.getFirst().data();
        assertThat(data.tableLabel()).isEqualTo("4");
        assertThat(data.items()).containsExactly("1× Hamburguesa");
        assertThat(ready.getFirst().companyId()).isEqualTo(bar.getId());

        mvc.perform(getJson("/api/service/floor").with(as(waiter)))
                .andExpect(jsonPath("$[0].tables[0].session.readyCount").value(1));

        // El camarero lo sirve: desaparece de la pantalla de cocina
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "SERVIDO")).with(as(waiter))).andExpect(status().isNoContent());
        mvc.perform(getJson("/api/kitchen?station=COCINA").with(as(cook))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("el camarero no puede anular algo que ya se prepara: 409; algo pendiente sí")
    void cancelOnlyPending() throws Exception {
        JsonNode session = tableWithRound(line(burger) + "," + line(burger));
        long sessionId = session.get("id").asLong();
        long started = session.get("lines").get(0).get("id").asLong();
        long pending = session.get("lines").get(1).get("id").asLong();
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", started, "PREPARANDO")).with(as(cook)));

        mvc.perform(delete("/api/service/sessions/{id}/lines/{line}", sessionId, started).with(as(waiter)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/service/sessions/{id}/lines/{line}", sessionId, pending).with(as(waiter)))
                .andExpect(status().isOk());
        // Lo anulado ya no sale en cocina
        mvc.perform(getJson("/api/kitchen?station=COCINA").with(as(cook)))
                .andExpect(jsonPath("$[0].items.length()").value(1));
    }

    @Test
    @DisplayName("pedido para llevar de hoy: sale en cocina y su estado sigue a sus productos; no se 'sirve'")
    void takeaway() throws Exception {
        Customer luis = customer(bar, "Luis", "luis@correo.es");
        Order order = order(luis, LocalDate.now(), "21:00", OrderStatus.PENDIENTE, burger, 2);
        long lineId = order.getLines().getFirst().getId();

        mvc.perform(getJson("/api/kitchen").with(as(cook)))
                .andExpect(jsonPath("$[0].label").value("#" + order.getId()))
                .andExpect(jsonPath("$[0].source").value("LLEVAR"))
                .andExpect(jsonPath("$[0].detail").value("Luis · recoge 21:00"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2));

        mvc.perform(patchJson("/api/kitchen/items", move("LLEVAR", lineId, "PREPARANDO")).with(as(cook)));
        mvc.perform(getJson("/api/orders/{id}", order.getId()).with(as(user(bar, Role.GESTOR))))
                .andExpect(jsonPath("$.status").value("EN_PROCESO"));
        mvc.perform(patchJson("/api/kitchen/items", move("LLEVAR", lineId, "LISTO")).with(as(cook)));
        mvc.perform(getJson("/api/orders/{id}", order.getId()).with(as(user(bar, Role.GESTOR))))
                .andExpect(jsonPath("$.status").value("LISTO"));
        mvc.perform(patchJson("/api/kitchen/items", move("LLEVAR", lineId, "SERVIDO")).with(as(cook)))
                .andExpect(status().isConflict());
    }

    private String moveUnits(String source, long id, int units, String status) {
        return "{\"items\": [{\"source\": \"%s\", \"id\": %d, \"units\": %d}], \"status\": \"%s\"}".formatted(source, id, units, status);
    }

    @Test
    @DisplayName("de uno en uno: 3× pendientes, se pasa 1 (quedan 2); el siguiente se junta con el que ya se prepara; el total no cambia")
    void oneUnitAtATime() throws Exception {
        JsonNode session = tableWithRound("{\"productId\": %d, \"quantity\": 3}".formatted(burger.getId()));
        long sessionId = session.get("id").asLong();
        long id = session.get("lines").get(0).get("id").asLong();

        mvc.perform(patchJson("/api/kitchen/items", moveUnits("MESA", id, 1, "PREPARANDO")).with(as(cook)))
                .andExpect(status().isNoContent());
        mvc.perform(getJson("/api/service/sessions/{id}", sessionId).with(as(waiter)))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].status").value("PENDIENTE"))
                .andExpect(jsonPath("$.lines[1].quantity").value(1))
                .andExpect(jsonPath("$.lines[1].status").value("PREPARANDO"))
                .andExpect(jsonPath("$.lines[1].round").value(1))
                .andExpect(jsonPath("$.total").value(28.5));

        mvc.perform(patchJson("/api/kitchen/items", moveUnits("MESA", id, 1, "PREPARANDO")).with(as(cook)));
        mvc.perform(getJson("/api/service/sessions/{id}", sessionId).with(as(waiter)))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].quantity").value(1))
                .andExpect(jsonPath("$.lines[1].quantity").value(2))
                .andExpect(jsonPath("$.lines[1].status").value("PREPARANDO"));

        // La ultima pasa entera y se junta: una sola linea de 3 preparandose
        mvc.perform(patchJson("/api/kitchen/items", moveUnits("MESA", id, 1, "PREPARANDO")).with(as(cook)));
        mvc.perform(getJson("/api/service/sessions/{id}", sessionId).with(as(waiter)))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(28.5));
    }

    @Test
    @DisplayName("listo de uno en uno: el aviso al camarero dice cuántas unidades; pedir más de las que quedan: 409")
    void readyUnits() throws Exception {
        long id = tableWithRound("{\"productId\": %d, \"quantity\": 2}".formatted(burger.getId())).get("lines").get(0).get("id").asLong();
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "PREPARANDO")).with(as(cook)));
        mvc.perform(patchJson("/api/kitchen/items", moveUnits("MESA", id, 3, "LISTO")).with(as(cook)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Solo quedan 2 de Hamburguesa"));
        mvc.perform(patchJson("/api/kitchen/items", moveUnits("MESA", id, 1, "LISTO")).with(as(cook)))
                .andExpect(status().isNoContent());

        var data = (LiveEvent.ReadyData) events.stream(LiveEvent.class).filter(e -> e.name().equals("ready")).findFirst().orElseThrow().data();
        assertThat(data.items()).containsExactly("1× Hamburguesa");
    }

    @Test
    @DisplayName("para llevar de uno en uno: el pedido pasa a en proceso y al acabar queda una sola línea")
    void takeawayUnits() throws Exception {
        Order order = order(customer(bar, "Ana", "ana@correo.es"), LocalDate.now(), "21:00", OrderStatus.PENDIENTE, burger, 2);
        long lineId = order.getLines().getFirst().getId();
        User manager = user(bar, Role.GESTOR);

        mvc.perform(patchJson("/api/kitchen/items", moveUnits("LLEVAR", lineId, 1, "PREPARANDO")).with(as(cook)));
        mvc.perform(getJson("/api/orders/{id}", order.getId()).with(as(manager)))
                .andExpect(jsonPath("$.status").value("EN_PROCESO"))
                .andExpect(jsonPath("$.lines.length()").value(2));
        mvc.perform(patchJson("/api/kitchen/items", moveUnits("LLEVAR", lineId, 1, "PREPARANDO")).with(as(cook)));
        mvc.perform(getJson("/api/orders/{id}", order.getId()).with(as(manager)))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(19.0));
    }

    @Test
    @DisplayName("cada producto lleva su categoría y color (dos productos con el mismo nombre se distinguen)")
    void category() throws Exception {
        tableWithRound(line(burger));
        mvc.perform(getJson("/api/kitchen").with(as(cook)))
                .andExpect(jsonPath("$[0].items[0].categoryName").value("Comida"))
                .andExpect(jsonPath("$[0].items[0].categoryColor").value("#F59E0B"));
    }

    @Test
    @DisplayName("el usuario de cocina solo usa su pantalla: productos, pedidos o mesas, 403; la cocina, los avisos y su perfil, sí")
    void kitchenOnly() throws Exception {
        mvc.perform(getJson("/api/products").with(as(cook))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/orders").with(as(cook))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/service/floor").with(as(cook))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/dashboard/dining").with(as(cook))).andExpect(status().isForbidden());
        mvc.perform(getJson("/api/kitchen").with(as(cook))).andExpect(status().isOk());
        mvc.perform(getJson("/api/auth/me").with(as(cook))).andExpect(status().isOk());
        // El camarero sigue leyendo la carta
        mvc.perform(getJson("/api/products").with(as(waiter))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("permisos: cocina no abre mesas (403); el visor no toca la cocina (403); otra empresa: 404")
    void permissions() throws Exception {
        long id = tableWithRound(line(burger)).get("lines").get(0).get("id").asLong();
        mvc.perform(postJson("/api/service/sessions", "{\"tableIds\": [%d], \"guests\": 2}".formatted(table.getId())).with(as(cook)))
                .andExpect(status().isForbidden());
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "PREPARANDO")).with(as(user(bar, Role.VISOR))))
                .andExpect(status().isForbidden());
        Company other = company("Otro");
        other.setType(BusinessType.BAR);
        mvc.perform(patchJson("/api/kitchen/items", move("MESA", id, "PREPARANDO")).with(as(user(other, Role.COCINA))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("en cocina, los productos van por el orden de cocina de su categoría (entrantes primero) y con sus extras")
    void kitchenOrderAndExtras() throws Exception {
        Category entrantes = category(bar, "Entrantes");
        Product bravas = product(entrantes, "Bravas", "4.50");
        Category food = burger.getCategory();
        mvc.perform(putJson("/api/categories/kitchen-order", "{\"ids\": [%d, %d]}".formatted(entrantes.getId(), food.getId()))
                .with(as(user(bar, Role.ADMIN)))).andExpect(status().isOk());
        com.pangestion.model.Ingredient huevo = ingredient(bar, "Huevo", "Huevo");
        huevo.setExtraPrice(new java.math.BigDecimal("1.00"));
        ingredientRepository.save(huevo);
        com.pangestion.model.Ingredient cebolla = ingredient(bar, "Cebolla", null);
        burger.getIngredients().add(cebolla);
        productRepository.save(burger);

        JsonNode session = tableWithRound("""
                {"productId": %d, "quantity": 1, "removedIngredientIds": [%d], "addedIngredientIds": [%d]},
                {"productId": %d, "quantity": 1}
                """.formatted(burger.getId(), cebolla.getId(), huevo.getId(), bravas.getId()));
        assertThat(session.get("lines").get(0).get("unitPrice").asDouble()).isEqualTo(10.50);

        mvc.perform(getJson("/api/kitchen?station=COCINA").with(as(cook)))
                .andExpect(jsonPath("$[0].items[0].productName").value("Bravas"))
                .andExpect(jsonPath("$[0].items[1].productName").value("Hamburguesa"))
                .andExpect(jsonPath("$[0].items[1].extras[0]").value("Huevo"))
                .andExpect(jsonPath("$[0].items[1].removedIngredients[0]").value("Cebolla"));
    }

    @Test
    @DisplayName("barra: la caña pasa de pendiente a lista sin preparando y avisa al camarero; la hamburguesa no puede saltárselo")
    void barGoesStraightToReady() throws Exception {
        JsonNode session = tableWithRound(line(burger) + "," + line(beer));
        long burgerId = session.get("lines").get(0).get("id").asLong();
        long beerId = session.get("lines").get(1).get("id").asLong();

        mvc.perform(patchJson("/api/kitchen/items", move("MESA", beerId, "LISTO")).with(as(cook)))
                .andExpect(status().isNoContent());
        mvc.perform(getJson("/api/kitchen?station=BARRA").with(as(cook)))
                .andExpect(jsonPath("$[0].items[0].status").value("LISTO"));
        assertThat(events.stream(LiveEvent.class).filter(e -> e.name().equals("ready")).count()).isEqualTo(1);

        mvc.perform(patchJson("/api/kitchen/items", move("MESA", burgerId, "LISTO")).with(as(cook)))
                .andExpect(status().isConflict());
    }
}
