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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbc;

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
                .andExpect(jsonPath("$[1].name").value("Bar Mosset"))
                // Datos internos del negocio: no salen en la web publica
                .andExpect(jsonPath("$[0].taxId").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].userCount").doesNotExist())
                .andExpect(jsonPath("$[0].customerCount").doesNotExist());
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
                .andExpect(jsonPath("$.customerName").value("Lola"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.total").value(16.00))
                .andExpect(jsonPath("$.lines[0].removedIngredients[0]").value("Cebolla"));

        assertThat(customerRepository.findByCompanyIdAndEmailIgnoreCase(bar.getId(), "lola@correo.es"))
                .hasValueSatisfying(c -> assertThat(c.getPhone()).isEqualTo("600000000"));
        // En la base de datos el telefono se guarda cifrado
        customerRepository.flush();
        assertThat(jdbc.queryForObject("select phone from customers where email = 'lola@correo.es'", String.class))
                .startsWith("enc:v1:").doesNotContain("600000000");
    }

    @Test
    @DisplayName("un cliente que repite (mismo email) no se duplica ni se cambian sus datos: solo se completa el teléfono si no tenía")
    void returningCustomer() throws Exception {
        Customer existing = customer(bar, "Lola", "lola@correo.es");

        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), TOMORROW, "13:00", "LOLA@correo.es", "Lola Ferrer", line(vegetal, 1, ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Lola Ferrer"));

        assertThat(customerRepository.countByCompanyId(bar.getId())).isEqualTo(1);
        assertThat(orderRepository.findAll()).allMatch(o -> o.getCustomer().getId().equals(existing.getId()));
        assertThat(existing.getName()).isEqualTo("Lola");
        assertThat(existing.getPhone()).isEqualTo("600000000");
    }

    @Test
    @DisplayName("quien conoce el email de un cliente no puede cambiar su teléfono ni leerlo en la respuesta")
    void customerTampering() throws Exception {
        Customer existing = customer(bar, "Lola", "lola@correo.es");
        existing.setPhone("611111111");
        customerRepository.save(existing);

        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), TOMORROW, "13:00", "lola@correo.es", "Otra", line(vegetal, 1, ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerPhone").doesNotExist());

        Customer reloaded = customerRepository.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Lola");
        assertThat(reloaded.getPhone()).isEqualTo("611111111");
    }

    @Test
    @DisplayName("bots: si llega relleno el campo trampa 'website', 400")
    void honeypot() throws Exception {
        String body = order(bar.getId(), TOMORROW, "13:00", "bot@spam.es", "Bot", line(vegetal, 1, ""))
                .replace("\"lines\"", "\"website\": \"http://spam\", \"lines\"");
        mvc.perform(postJson("/api/public/orders", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.website").exists());
    }

    @Test
    @DisplayName("el mismo email en otro negocio es otro cliente")
    void sameEmailOtherCompany() throws Exception {
        Company forn = company("Forn");
        Customer fornLola = customer(forn, "Lola", "lola@correo.es");

        mvc.perform(postJson("/api/public/orders",
                        order(bar.getId(), TOMORROW, "13:00", "lola@correo.es", "Lola", line(vegetal, 1, ""))))
                .andExpect(status().isCreated());

        assertThat(customerRepository.countByCompanyId(bar.getId())).isEqualTo(1);
        assertThat(customerRepository.findById(fornLola.getId()).orElseThrow().getCompany().getId()).isEqualTo(forn.getId());
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

    @Test
    @DisplayName("extras de un negocio: solo los ingredientes con precio de extra")
    void extras() throws Exception {
        Company bar = company("Bar de extras");
        Ingredient huevo = ingredient(bar, "Huevo", "Huevo");
        huevo.setExtraPrice(new java.math.BigDecimal("1.00"));
        ingredientRepository.save(huevo);
        ingredient(bar, "Sal", null);
        mvc.perform(getJson("/api/public/companies/{id}/extras", bar.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Huevo"))
                .andExpect(jsonPath("$[0].price").value(1.00));
    }
}
