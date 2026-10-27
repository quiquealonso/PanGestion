package com.pangestion.web;

import com.pangestion.model.Company;
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

/** Alta y gestion de empresas por el SUPERADMIN: /api/companies. */
@DisplayName("API · Empresas (SUPERADMIN)")
class CompanyApiTest extends ApiTestSupport {

    private User superAdmin;
    private Company forn;

    @BeforeEach
    void setUp() {
        superAdmin = user(null, Role.SUPERADMIN);
        forn = company("Forn Campanero");
    }

    private static String body(String name, String taxId, String type, String open, String close) {
        return """
                {"name": "%s", "type": "%s", "taxId": "%s", "email": "hola@test.es", "phone": "960000000",
                 "address": "Calle Mayor 1", "openingTime": "%s", "closingTime": "%s",
                 "slotMinutes": 30, "ordersPerSlot": 4, "active": true}
                """.formatted(name, type, taxId, open, close);
    }

    @Test
    @DisplayName("listar: todas las empresas por nombre, con usuarios y clientes")
    void list() throws Exception {
        company("Agua Bar");
        user(forn, Role.ADMIN);
        customer(forn, "Lola", "lola@correo.es");

        mvc.perform(getJson("/api/companies").with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Agua Bar"))
                .andExpect(jsonPath("$[1].name").value("Forn Campanero"))
                .andExpect(jsonPath("$[1].userCount").value(1))
                .andExpect(jsonPath("$[1].customerCount").value(1));
    }

    @Test
    @DisplayName("ver una: 200; una que no existe: 404")
    void findById() throws Exception {
        mvc.perform(getJson("/api/companies/{id}", forn.getId()).with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingTime").value("08:00"))
                .andExpect(jsonPath("$.type").value("PANADERIA"));
        mvc.perform(getJson("/api/companies/{id}", 999_999).with(as(superAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Empresa con id 999999 no encontrado"));
    }

    @Test
    @DisplayName("crear: 201, el CIF se guarda en mayúsculas y con su tipo de negocio")
    void create() throws Exception {
        mvc.perform(postJson("/api/companies", body("El Mosset", " b12345678 ", "BAR", "08:00", "23:30")).with(as(superAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.taxId").value("B12345678"))
                .andExpect(jsonPath("$.type").value("BAR"))
                .andExpect(jsonPath("$.closingTime").value("23:30"));
    }

    @Test
    @DisplayName("crear: CIF repetido (sin distinguir mayúsculas): 409")
    void createDuplicateTaxId() throws Exception {
        mvc.perform(postJson("/api/companies", body("Otra", forn.getTaxId().toLowerCase(), "BAR", "08:00", "20:00")).with(as(superAdmin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("crear: la apertura tiene que ser antes del cierre (validación propia @ValidOpeningHours): 400")
    void createInvalidHours() throws Exception {
        mvc.perform(postJson("/api/companies", body("Otra", "B99999999", "BAR", "20:00", "08:00")).with(as(superAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.closingTime").value("debe ser posterior a la hora de apertura"));
        mvc.perform(postJson("/api/companies", body("Otra", "B99999999", "BAR", "08:00", "08:00")).with(as(superAdmin)))
                .andExpect(status().isBadRequest());
        assertThat(companyRepository.findAll()).extracting(Company::getName).doesNotContain("Otra");
    }

    @Test
    @DisplayName("crear: 201 con la cabecera Location del detalle")
    void createLocation() throws Exception {
        String location = mvc.perform(postJson("/api/companies", body("Nova", "B77777777", "BAR", "08:00", "20:00")).with(as(superAdmin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        Long id = companyRepository.findAll().stream().filter(c -> c.getName().equals("Nova")).findFirst().orElseThrow().getId();

        assertThat(location).isEqualTo("http://localhost/api/companies/" + id);
    }

    @Test
    @DisplayName("crear: datos no válidos (sin nombre, franja de 5 min, email mal escrito): 400")
    void createValidation() throws Exception {
        mvc.perform(postJson("/api/companies", """
                        {"name": "", "type": "BAR", "taxId": "B1", "email": "no-es-email", "address": "x",
                         "openingTime": "08:00", "closingTime": "20:00", "slotMinutes": 5, "ordersPerSlot": 0, "active": true}
                        """).with(as(superAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.slotMinutes").exists())
                .andExpect(jsonPath("$.fieldErrors.ordersPerSlot").exists());
    }

    @Test
    @DisplayName("editar: cambia los datos y el tipo; mantener su propio CIF no es un duplicado")
    void update() throws Exception {
        mvc.perform(putJson("/api/companies/{id}", body("Forn Nou", forn.getTaxId(), "RESTAURANTE", "07:00", "15:00"), forn.getId())
                        .with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Forn Nou"))
                .andExpect(jsonPath("$.type").value("RESTAURANTE"));
    }

    @Test
    @DisplayName("editar: usar el CIF de otra empresa: 409")
    void updateDuplicateTaxId() throws Exception {
        Company other = company("Otra");
        mvc.perform(putJson("/api/companies/{id}", body("Forn", other.getTaxId(), "PANADERIA", "08:00", "20:00"), forn.getId())
                        .with(as(superAdmin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("eliminar una empresa vacía: 204")
    void delete() throws Exception {
        mvc.perform(deleteRequest("/api/companies/{id}", forn.getId()).with(as(superAdmin)))
                .andExpect(status().isNoContent());
        assertThat(companyRepository.findById(forn.getId())).isEmpty();
    }

    @Test
    @DisplayName("eliminar una empresa con usuarios o pedidos: 409 (hay que desactivarla)")
    void deleteWithData() throws Exception {
        user(forn, Role.ADMIN);
        mvc.perform(deleteRequest("/api/companies/{id}", forn.getId()).with(as(superAdmin)))
                .andExpect(status().isConflict());

        Company withOrders = company("Con pedidos");
        var pa = product(category(withOrders, "Pans"), "Pa", "1.00");
        order(customer(withOrders, "Lola", "lola@correo.es"), TOMORROW, "09:00", OrderStatus.PENDIENTE, pa, 1);
        mvc.perform(deleteRequest("/api/companies/{id}", withOrders.getId()).with(as(superAdmin)))
                .andExpect(status().isConflict());
    }
}
