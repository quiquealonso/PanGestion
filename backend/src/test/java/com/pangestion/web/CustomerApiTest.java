package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Clientes de la empresa: /api/customers. */
@DisplayName("API · Clientes")
class CustomerApiTest extends ApiTestSupport {

    private Company forn;
    private User gestor;
    private Customer lola;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        gestor = user(forn, Role.GESTOR);
        lola = customer(forn, "Lola Ferrer", "lola@correo.es");
    }

    private static String body(String name, String email, String businessName, String taxId) {
        return """
                {"name": "%s", "email": "%s", "phone": "600111222", "businessName": %s, "taxId": %s}
                """.formatted(name, email,
                businessName == null ? "null" : "\"" + businessName + "\"",
                taxId == null ? "null" : "\"" + taxId + "\"");
    }

    @Test
    @DisplayName("buscar por nombre, email o razón social; sin texto, todos por nombre")
    void search() throws Exception {
        Customer hotel = customer(forn, "Marta", "compras@hotel.es");
        hotel.setBusinessName("Hotel Central SL");

        mvc.perform(getJson("/api/customers").with(as(gestor)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Lola Ferrer"));
        mvc.perform(getJson("/api/customers?search=FERRER").with(as(gestor))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getJson("/api/customers?search=hotel.es").with(as(gestor))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getJson("/api/customers?search=central").with(as(gestor)))
                .andExpect(jsonPath("$[0].businessName").value("Hotel Central SL"));
    }

    @Test
    @DisplayName("ver uno: 200; de otra empresa: 404")
    void findById() throws Exception {
        mvc.perform(getJson("/api/customers/{id}", lola.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").exists());
        Customer other = customer(company("Bar"), "Pep", "pep@correo.es");
        mvc.perform(getJson("/api/customers/{id}", other.getId()).with(as(gestor))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crear un particular: email en minúsculas; razón social y CIF en blanco = null")
    void createPerson() throws Exception {
        mvc.perform(postJson("/api/customers", body("Pep Soler", "Pep@Correo.ES", " ", "")).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pep@correo.es"))
                .andExpect(jsonPath("$.businessName").doesNotExist())
                .andExpect(jsonPath("$.taxId").doesNotExist());
    }

    @Test
    @DisplayName("crear un negocio: guarda razón social y CIF")
    void createBusiness() throws Exception {
        mvc.perform(postJson("/api/customers", body("Marta", "compras@hotel.es", "Hotel Central SL", "B11111111")).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessName").value("Hotel Central SL"))
                .andExpect(jsonPath("$.taxId").value("B11111111"));
    }

    @Test
    @DisplayName("crear: email repetido en la empresa: 409; el mismo email en otra empresa es otro cliente")
    void createDuplicateEmail() throws Exception {
        mvc.perform(postJson("/api/customers", body("Otra Lola", "LOLA@correo.es", null, null)).with(as(gestor)))
                .andExpect(status().isConflict());

        User barGestor = user(company("Bar"), Role.GESTOR);
        mvc.perform(postJson("/api/customers", body("Lola", "lola@correo.es", null, null)).with(as(barGestor)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("crear: email no válido o sin nombre: 400")
    void createValidation() throws Exception {
        mvc.perform(postJson("/api/customers", body("", "no-es-email", null, null)).with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("editar: cambia datos; usar el email de otro cliente: 409")
    void update() throws Exception {
        mvc.perform(putJson("/api/customers/{id}", body("Lola F.", "lola@correo.es", null, null), lola.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lola F."))
                .andExpect(jsonPath("$.phone").value("600111222"));

        customer(forn, "Pep", "pep@correo.es");
        mvc.perform(putJson("/api/customers/{id}", body("Lola", "pep@correo.es", null, null), lola.getId()).with(as(gestor)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("historial: sus pedidos, del más reciente al más antiguo")
    void orders() throws Exception {
        var pa = product(category(forn, "Pans"), "Pa", "1.00");
        order(lola, TOMORROW, "09:00", OrderStatus.PENDIENTE, pa, 1);
        order(lola, TOMORROW.plusDays(3), "10:00", OrderStatus.PENDIENTE, pa, 2);

        mvc.perform(getJson("/api/customers/{id}/orders", lola.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pickupDate").value(TOMORROW.plusDays(3).toString()));
    }

    @Test
    @DisplayName("listado: cada cliente trae nº de pedidos, total gastado y último pedido (sin cancelados)")
    void listIncludesOrderSummary() throws Exception {
        var pa = product(category(forn, "Pans"), "Pa", "1.50");
        order(lola, TOMORROW, "09:00", OrderStatus.PENDIENTE, pa, 2);
        order(lola, TOMORROW.plusDays(2), "10:00", OrderStatus.ENTREGADO, pa, 1);
        order(lola, TOMORROW.plusDays(5), "10:00", OrderStatus.CANCELADO, pa, 4);
        customer(forn, "Pep", "pep@correo.es");

        mvc.perform(getJson("/api/customers?search=lola").with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderCount").value(2))
                .andExpect(jsonPath("$[0].totalSpent").value(4.5))
                .andExpect(jsonPath("$[0].lastOrderDate").value(TOMORROW.plusDays(2).toString()));
        mvc.perform(getJson("/api/customers?search=pep").with(as(gestor)))
                .andExpect(jsonPath("$[0].orderCount").value(0))
                .andExpect(jsonPath("$[0].totalSpent").value(0))
                .andExpect(jsonPath("$[0].lastOrderDate").doesNotExist());
    }

    @Test
    @DisplayName("historial de un cliente de otra empresa: 404")
    void ordersOfOtherCompany() throws Exception {
        Customer other = customer(company("Bar"), "Pep", "pep@correo.es");
        mvc.perform(getJson("/api/customers/{id}/orders", other.getId()).with(as(gestor))).andExpect(status().isNotFound());
    }

    // ----- Creacion anidada (S9): POST /api/customers/{id}/orders -----

    private String nestedOrder(String extraField, Long productId) {
        return """
                {%s"pickupDate": "%s", "pickupTime": "10:00", "notes": null,
                 "lines": [{"productId": %d, "quantity": 2, "removedIngredientIds": []}]}
                """.formatted(extraField, TOMORROW, productId);
    }

    @Test
    @DisplayName("pedido anidado: la ruta decide el cliente; 201 y Location al detalle canónico /api/orders/{id}")
    void nestedOrder() throws Exception {
        var pa = product(category(forn, "Pans"), "Pa", "1.00");

        String location = mvc.perform(postJson("/api/customers/{id}/orders", nestedOrder("", pa.getId()), lola.getId()).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(lola.getId()))
                .andExpect(jsonPath("$.total").value(2.00))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).matches("http://localhost/api/orders/\\d+");
        mvc.perform(getJson(location.replace("http://localhost", "")).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Lola Ferrer"));
    }

    @Test
    @DisplayName("pedido anidado: el cuerpo no puede decir otro cliente (customerId no está permitido): 400")
    void nestedOrderRejectsCustomerIdInBody() throws Exception {
        var pa = product(category(forn, "Pans"), "Pa", "1.00");
        Customer pep = customer(forn, "Pep", "pep@correo.es");

        mvc.perform(postJson("/api/customers/{id}/orders", nestedOrder("\"customerId\": " + pep.getId() + ", ", pa.getId()), lola.getId())
                        .with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campo no permitido: customerId"));
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("pedido anidado para un cliente de otra empresa o que no existe: 404, sin crear nada")
    void nestedOrderUnknownCustomer() throws Exception {
        var pa = product(category(forn, "Pans"), "Pa", "1.00");
        Customer other = customer(company("Bar"), "Pep", "pep@correo.es");

        mvc.perform(postJson("/api/customers/{id}/orders", nestedOrder("", pa.getId()), other.getId()).with(as(gestor)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/customers/{id}/orders", nestedOrder("", pa.getId()), 999_999).with(as(gestor)))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("eliminar sin pedidos: 204; con pedidos: 409")
    void delete() throws Exception {
        Customer pep = customer(forn, "Pep", "pep@correo.es");
        mvc.perform(deleteRequest("/api/customers/{id}", pep.getId()).with(as(gestor))).andExpect(status().isNoContent());
        assertThat(customerRepository.findById(pep.getId())).isEmpty();

        order(lola, TOMORROW, "09:00", OrderStatus.PENDIENTE, product(category(forn, "Pans"), "Pa", "1.00"), 1);
        mvc.perform(deleteRequest("/api/customers/{id}", lola.getId()).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede eliminar un cliente con pedidos"));
    }
}
