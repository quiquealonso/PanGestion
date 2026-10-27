package com.pangestion.web;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Ingredient;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedidos desde el panel de gestion: /api/orders (la produccion va en ProductionApiTest). */
@DisplayName("API · Pedidos")
class OrderApiTest extends ApiTestSupport {

    private Company bar;
    private User gestor;
    private Customer lola;
    private Product pizza;
    private Product bravas;
    private Ingredient cebolla;
    private Ingredient formatge;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        gestor = user(bar, Role.GESTOR);
        lola = customer(bar, "Lola Ferrer", "lola@correo.es");
        cebolla = ingredient(bar, "Cebolla", null);
        formatge = ingredient(bar, "Queso", "Lácteos");
        Category pizzas = category(bar, "Pizzas");
        pizza = product(pizzas, "Barbacoa", "8.00", cebolla, formatge);
        bravas = product(category(bar, "Entrantes"), "Bravas", "4.50");
    }

    private String body(Long customerId, LocalDate date, String time, String lines) {
        return """
                {"customerId": %d, "pickupDate": "%s", "pickupTime": "%s", "notes": "Sin prisa", "lines": [%s]}
                """.formatted(customerId, date, time, lines);
    }

    private static String line(Product product, int quantity, Ingredient... removed) {
        StringBuilder ids = new StringBuilder();
        for (Ingredient ingredient : removed) {
            ids.append(ids.isEmpty() ? "" : ",").append(ingredient.getId());
        }
        return "{\"productId\": %d, \"quantity\": %d, \"removedIngredientIds\": [%s]}".formatted(product.getId(), quantity, ids);
    }

    // ----- Crear -----

    @Test
    @DisplayName("crear: 201, PENDIENTE, con el total, las unidades y el precio de cada línea")
    void create() throws Exception {
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(pizza, 2) + "," + line(bravas, 1))).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesRegex("http://localhost/api/orders/\\d+")))
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.customerName").value("Lola Ferrer"))
                .andExpect(jsonPath("$.pickupTime").value("13:00"))
                .andExpect(jsonPath("$.total").value(20.50))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(8.00))
                .andExpect(jsonPath("$.lines[0].subtotal").value(16.00))
                .andExpect(jsonPath("$.notes").value("Sin prisa"));
    }

    @Test
    @DisplayName("quitar ingredientes: 'sin cebolla' es una línea aparte; las líneas iguales se suman")
    void customizedLines() throws Exception {
        String lines = String.join(",",
                line(pizza, 1), line(pizza, 1, cebolla), line(pizza, 2), line(pizza, 1, formatge, cebolla));

        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "19:30", lines)).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].removedIngredients.length()").value(0))
                .andExpect(jsonPath("$.lines[1].removedIngredients[0]").value("Cebolla"))
                .andExpect(jsonPath("$.lines[2].removedIngredients[0]").value("Cebolla"))
                .andExpect(jsonPath("$.lines[2].removedIngredients[1]").value("Queso"))
                .andExpect(jsonPath("$.total").value(40.00));
    }

    @Test
    @DisplayName("quitar un ingrediente que el producto no lleva: 409")
    void removeIngredientNotInProduct() throws Exception {
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(bravas, 1, cebolla))).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El producto Bravas no lleva ese ingrediente"));
    }

    @Test
    @DisplayName("un producto desactivado no se puede pedir: 409")
    void inactiveProduct() throws Exception {
        bravas.setActive(false);
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(bravas, 1))).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El producto Bravas no esta disponible"));
    }

    @Test
    @DisplayName("un producto o un cliente de otra empresa: 404")
    void foreignProductOrCustomer() throws Exception {
        Company forn = company("Forn");
        Product pa = product(category(forn, "Pans"), "Pa", "1.00");
        Customer pep = customer(forn, "Pep", "pep@correo.es");

        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(pa, 1))).with(as(gestor)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/orders", body(pep.getId(), TOMORROW, "13:00", line(pizza, 1))).with(as(gestor)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("franjas: hora fuera de horario, día pasado o franja completa: 409")
    void slotRules() throws Exception {
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "22:00", line(pizza, 1))).with(as(gestor)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/orders", body(lola.getId(), LocalDate.now().minusDays(1), "13:00", line(pizza, 1))).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La franja elegida ya ha pasado"));

        order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 1);
        order(lola, TOMORROW, "13:00", OrderStatus.CANCELADO, pizza, 1); // los cancelados no ocupan
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(pizza, 1))).with(as(gestor)))
                .andExpect(status().isCreated());
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(pizza, 1))).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La franja de las 13:00 esta completa"));
    }

    @Test
    @DisplayName("crear: sin líneas, cantidad 0 o sin cliente: 400")
    void createValidation() throws Exception {
        mvc.perform(postJson("/api/orders", """
                        {"customerId": null, "pickupDate": "%s", "pickupTime": "13:00", "lines": []}
                        """.formatted(TOMORROW)).with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").exists())
                .andExpect(jsonPath("$.fieldErrors.lines").exists());
        mvc.perform(postJson("/api/orders", body(lola.getId(), TOMORROW, "13:00", line(pizza, 0))).with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['lines[0].quantity']").exists());
    }

    // ----- Consultar -----

    @Test
    @DisplayName("buscar: por cliente o número, estado y fechas")
    void search() throws Exception {
        Customer pep = customer(bar, "Pep Soler", "pep@correo.es");
        Order first = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 1);
        order(pep, TOMORROW.plusDays(1), "13:00", OrderStatus.LISTO, pizza, 1);

        mvc.perform(getJson("/api/orders").with(as(gestor))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(getJson("/api/orders?search=soler").with(as(gestor)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].customerName").value("Pep Soler"));
        mvc.perform(getJson("/api/orders?search=" + first.getId()).with(as(gestor)))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(first.getId())).exists());
        mvc.perform(getJson("/api/orders?status=LISTO").with(as(gestor))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getJson("/api/orders?from={d}&to={d}", TOMORROW, TOMORROW).with(as(gestor)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(first.getId()));
    }

    @Test
    @DisplayName("buscar con un estado que no existe: 400")
    void searchBadStatus() throws Exception {
        mvc.perform(getJson("/api/orders?status=PERDIDO").with(as(gestor))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ver uno: 200; de otra empresa: 404")
    void findById() throws Exception {
        Order order = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 2);
        mvc.perform(getJson("/api/orders/{id}", order.getId()).with(as(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Bar"));

        User fornGestor = user(company("Forn"), Role.GESTOR);
        mvc.perform(getJson("/api/orders/{id}", order.getId()).with(as(fornGestor))).andExpect(status().isNotFound());
    }

    // ----- Editar -----

    @Test
    @DisplayName("editar: cambia líneas y franja; su propio hueco no cuenta como ocupado")
    void update() throws Exception {
        Order order = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 1);
        order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 1); // franja llena (2/2) con este pedido dentro

        mvc.perform(putJson("/api/orders/{id}", body(lola.getId(), TOMORROW, "13:00", line(bravas, 4)), order.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(18.00))
                .andExpect(jsonPath("$.lines.length()").value(1));
        mvc.perform(putJson("/api/orders/{id}", body(lola.getId(), TOMORROW, "14:00", line(bravas, 4)), order.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupTime").value("14:00"));
    }

    @Test
    @DisplayName("editar: un producto que ya estaba se mantiene aunque se haya desactivado después")
    void updateKeepsDeactivatedProduct() throws Exception {
        Order order = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, bravas, 1);
        bravas.setActive(false);

        mvc.perform(putJson("/api/orders/{id}", body(lola.getId(), TOMORROW, "13:00", line(bravas, 2)), order.getId()).with(as(gestor)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("editar un pedido entregado o cancelado: 409")
    void updateClosedOrder() throws Exception {
        Order delivered = order(lola, TOMORROW, "13:00", OrderStatus.ENTREGADO, pizza, 1);
        mvc.perform(putJson("/api/orders/{id}", body(lola.getId(), TOMORROW, "13:00", line(pizza, 2)), delivered.getId()).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede editar un pedido entregado o cancelado"));
    }

    @Test
    @DisplayName("cambiar el estado")
    void changeStatus() throws Exception {
        Order order = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 1);

        for (String status : new String[]{"EN_PROCESO", "LISTO", "ENTREGADO"}) {
            mvc.perform(patchJson("/api/orders/{id}/status", "{\"status\": \"%s\"}".formatted(status), order.getId()).with(as(gestor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
        }
        mvc.perform(patchJson("/api/orders/{id}/status", "{\"status\": null}", order.getId()).with(as(gestor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("eliminar: 204")
    void delete() throws Exception {
        Order order = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 1);
        mvc.perform(deleteRequest("/api/orders/{id}", order.getId()).with(as(gestor))).andExpect(status().isNoContent());
        assertThat(orderRepository.findById(order.getId())).isEmpty();
        mvc.perform(deleteRequest("/api/orders/{id}", order.getId()).with(as(gestor))).andExpect(status().isNotFound());
    }
}
