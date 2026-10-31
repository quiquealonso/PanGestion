package com.pangestion.web;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Product;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web publica con las cuentas de cliente desactivadas (configuracion por defecto): se pide sin cuenta. */
@DisplayName("API · Web pública sin cuentas de cliente")
class PublicOrderWithoutAccountApiTest extends ApiTestSupport {

    private Company bar;
    private Product vegetal;

    @BeforeEach
    void setUp() {
        bar = company("Bar Mosset");
        Category pizzas = category(bar, "Pizzas");
        vegetal = product(pizzas, "Vegetal", "8.00");
    }

    private String order(String customer) {
        return """
                {"companyId": %d, "pickupDate": "%s", "pickupTime": "13:00", %s
                 "lines": [{"productId": %d, "quantity": 1}]}
                """.formatted(bar.getId(), TOMORROW, customer, vegetal.getId());
    }

    @Test
    @DisplayName("la web pública sabe que no hace falta cuenta, y las rutas de cuentas no existen (404)")
    void settings() throws Exception {
        mvc.perform(getJson("/api/public/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerAccounts").value(false));
        mvc.perform(postJson("/api/public/account/register", "{\"name\": \"A\", \"email\": \"a@b.es\", \"password\": \"unaclave123\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("pedido sin cuenta, con nombre y email en el formulario: 201 y crea el cliente")
    void orderWithoutAccount() throws Exception {
        mvc.perform(postJson("/api/public/orders",
                        order("\"customer\": {\"name\": \"Lola\", \"email\": \"Lola@Correo.es\", \"phone\": \"600000000\"},")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Lola"))
                .andExpect(jsonPath("$.customerEmail").doesNotExist());
        assertThat(customerRepository.findByCompanyIdAndEmailIgnoreCase(bar.getId(), "lola@correo.es"))
                .hasValueSatisfying(c -> assertThat(c.getPhone()).isEqualTo("600000000"));
    }

    @Test
    @DisplayName("quien conoce el email de un cliente no cambia sus datos ni los ve en la respuesta")
    void customerTampering() throws Exception {
        Customer existing = customer(bar, "Lola", "lola@correo.es");
        existing.setPhone("611111111");
        customerRepository.save(existing);

        mvc.perform(postJson("/api/public/orders",
                        order("\"customer\": {\"name\": \"Otra\", \"email\": \"lola@correo.es\", \"phone\": \"699999999\"},")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerPhone").doesNotExist());
        Customer reloaded = customerRepository.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Lola");
        assertThat(reloaded.getPhone()).isEqualTo("611111111");
    }

    @Test
    @DisplayName("sin datos del cliente: 409; con un email mal escrito: 400")
    void customerRequired() throws Exception {
        mvc.perform(postJson("/api/public/orders", order(""))).andExpect(status().isConflict());
        mvc.perform(postJson("/api/public/orders", order("\"customer\": {\"name\": \"Lola\", \"email\": \"no-es-email\"},")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['customer.email']").exists());
    }
}
