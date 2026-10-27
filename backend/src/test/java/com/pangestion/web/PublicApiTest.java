package com.pangestion.web;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Ingredient;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web publica de pedidos, sin login: /api/public/**. */
@DisplayName("API · Web pública")
class PublicApiTest extends ApiTestSupport {

    private Company bar;
    private Category pizzas;
    private Product vegetal;
    private Ingredient cebolla;

    @BeforeEach
    void setUp() {
        bar = company("Bar Mosset");
        pizzas = category(bar, "Pizzas");
        cebolla = ingredient(bar, "Cebolla", null);
        vegetal = product(pizzas, "Vegetal", "8.00", cebolla,
                ingredient(bar, "Queso", "Lácteos"), ingredient(bar, "Mozzarella", "Lácteos"));
    }

    private String order(Long companyId, LocalDate date, String time, String email, String name, String lines) {
        return """
                {"companyId": %d, "pickupDate": "%s", "pickupTime": "%s",
                 "customer": {"name": "%s", "email": "%s", "phone": "600000000", "businessName": null, "taxId": null},
                 "notes": "La pizza bien hecha", "lines": [%s]}
                """.formatted(companyId, date, time, name, email, lines);
    }

    private String line(Product product, int quantity, String removedIds) {
        return "{\"productId\": %d, \"quantity\": %d, \"removedIngredientIds\": [%s]}".formatted(product.getId(), quantity, removedIds);
    }

    @Test
    @DisplayName("negocios: solo los que aceptan pedidos, por nombre")
    void companies() throws Exception {
        company("Agua Forn");
        company("Cerrado").setActive(false);

        mvc.perform(getJson("/api/public/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Cerrado')]").isEmpty())
                .andExpect(jsonPath("$[0].name").value("Agua Forn"))
                .andExpect(jsonPath("$[1].name").value("Bar Mosset"));
    }

    @Test
    @DisplayName("franjas de un negocio sin login; de uno que no existe: 404")
    void slots() throws Exception {
        mvc.perform(getJson("/api/public/companies/{id}/slots?date={d}", bar.getId(), TOMORROW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value("08:00"))
                .andExpect(jsonPath("$[0].available").value(true));
        mvc.perform(getJson("/api/public/companies/{id}/slots?date={d}", 999_999, TOMORROW)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("categorías del negocio")
    void categories() throws Exception {
        category(company("Otro"), "No sale");
        mvc.perform(getJson("/api/public/companies/{id}/categories", bar.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pizzas"));
    }

    @Test
    @DisplayName("productos: solo activos, con ingredientes (con id, para quitarlos) y alérgenos sin repetir")
    void products() throws Exception {
        product(pizzas, "Desactivada", "1.00").setActive(false);
        product(category(bar, "Entrantes"), "Bravas", "4.50");

        mvc.perform(getJson("/api/public/companies/{id}/products", bar.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Vegetal"))
                .andExpect(jsonPath("$[0].color").value("#F59E0B"))
                .andExpect(jsonPath("$[0].ingredients.length()").value(3))
                .andExpect(jsonPath("$[0].ingredients[0].id").value(cebolla.getId()))
                .andExpect(jsonPath("$[0].allergens.length()").value(1))
                .andExpect(jsonPath("$[0].allergens[0]").value("Lácteos"));
        mvc.perform(getJson("/api/public/companies/{id}/products?categoryId={c}", bar.getId(), pizzas.getId()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("hacer un pedido: 201, crea el cliente en ese negocio y guarda el 'sin cebolla'")
    void createOrder() throws Exception {
        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), TOMORROW, "13:00", "Lola@Correo.es", "Lola", line(vegetal, 2, cebolla.getId().toString()))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesRegex("http://localhost/api/orders/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.customerEmail").value("lola@correo.es"))
                .andExpect(jsonPath("$.total").value(16.00))
                .andExpect(jsonPath("$.lines[0].removedIngredients[0]").value("Cebolla"));

        assertThat(customerRepository.findByCompanyIdAndEmailIgnoreCase(bar.getId(), "lola@correo.es")).isPresent();
    }

    @Test
    @DisplayName("un cliente que repite (mismo email) no se duplica: se actualizan su nombre y teléfono")
    void returningCustomer() throws Exception {
        Customer existing = customer(bar, "Lola", "lola@correo.es");

        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), TOMORROW, "13:00", "LOLA@correo.es", "Lola Ferrer", line(vegetal, 1, ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(existing.getId()));

        assertThat(customerRepository.countByCompanyId(bar.getId())).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Lola Ferrer");
        assertThat(existing.getPhone()).isEqualTo("600000000");
    }

    @Test
    @DisplayName("el mismo email en otro negocio es otro cliente")
    void sameEmailOtherCompany() throws Exception {
        Company forn = company("Forn");
        Customer fornLola = customer(forn, "Lola", "lola@correo.es");

        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), TOMORROW, "13:00", "lola@correo.es", "Lola", line(vegetal, 1, ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(org.hamcrest.Matchers.not(fornLola.getId().intValue())));
    }

    @Test
    @DisplayName("reglas: negocio cerrado, franja completa o producto de otro negocio")
    void rules() throws Exception {
        Company closed = company("Cerrado");
        closed.setActive(false);
        Product other = product(category(closed, "X"), "X", "1.00");
        mvc.perform(postJson("/api/public/orders", order(closed.getId(), TOMORROW, "13:00", "a@b.es", "A", line(other, 1, ""))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cerrado no acepta pedidos ahora mismo"));

        Customer lola = customer(bar, "Lola", "lola@correo.es");
        order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, vegetal, 1);
        order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, vegetal, 1);
        mvc.perform(postJson("/api/public/orders", order(bar.getId(), TOMORROW, "13:00", "a@b.es", "A", line(vegetal, 1, ""))))
                .andExpect(status().isConflict());

        mvc.perform(postJson("/api/public/orders", order(bar.getId(), TOMORROW, "14:00", "a@b.es", "A", line(other, 1, ""))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("validación: fecha pasada, email mal escrito o sin productos: 400")
    void validation() throws Exception {
        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), LocalDate.now().minusDays(1), "13:00", "no-es-email", "", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pickupDate").exists())
                .andExpect(jsonPath("$.fieldErrors['customer.email']").exists())
                .andExpect(jsonPath("$.fieldErrors['customer.name']").exists())
                .andExpect(jsonPath("$.fieldErrors.lines").exists());
    }
}
