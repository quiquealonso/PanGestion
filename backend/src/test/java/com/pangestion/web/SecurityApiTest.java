package com.pangestion.web;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quien puede hacer que (SecurityConfig) y que cada empresa solo ve lo suyo (CurrentUser + servicios).
 */
@DisplayName("API · Seguridad, roles y aislamiento entre empresas")
class SecurityApiTest extends ApiTestSupport {

    private static final String CATEGORY = """
            {"name": "Nueva", "color": "#112233"}
            """;

    private Company forn;
    private Company bar;
    private User superAdmin;
    private User admin;
    private User gestor;
    private User visor;
    private Category barCategory;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        bar = company("Bar");
        superAdmin = user(null, Role.SUPERADMIN);
        admin = user(forn, Role.ADMIN);
        gestor = user(forn, Role.GESTOR);
        visor = user(forn, Role.VISOR);
        barCategory = category(bar, "Pizzas");
    }

    // ----- Autenticacion -----

    @Test
    @DisplayName("sin token, la API privada responde 401")
    void privateApiNeedsToken() throws Exception {
        mvc.perform(getJson("/api/categories")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/categories", CATEGORY)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token falso o mal firmado: 401")
    void invalidToken() throws Exception {
        mvc.perform(getJson("/api/categories").header("Authorization", "Bearer esto.no.vale"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login, web pública e imágenes no necesitan token")
    void publicEndpoints() throws Exception {
        mvc.perform(getJson("/api/public/companies")).andExpect(status().isOk());
        mvc.perform(postJson("/api/auth/login", """
                {"email": "x@test.es", "password": "x"}
                """)).andExpect(status().isUnauthorized()); // 401 por credenciales, no por falta de token
    }

    // ----- Roles -----

    @Test
    @DisplayName("VISOR: puede leer pero no escribir (403)")
    void visorReadOnly() throws Exception {
        mvc.perform(getJson("/api/categories").with(as(visor))).andExpect(status().isOk());
        mvc.perform(postJson("/api/categories", CATEGORY).with(as(visor))).andExpect(status().isForbidden());
        mvc.perform(postJson("/api/orders/start-day?date=" + TOMORROW, "").with(as(visor))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GESTOR: escribe en el catálogo, pero no gestiona usuarios ni los datos de la empresa")
    void gestorPermissions() throws Exception {
        mvc.perform(postJson("/api/categories", CATEGORY).with(as(gestor))).andExpect(status().isCreated());
        mvc.perform(getJson("/api/users").with(as(gestor))).andExpect(status().isForbidden());
        mvc.perform(putJson("/api/company", "{}").with(as(gestor))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN: gestiona su empresa y sus usuarios, pero no el alta de empresas")
    void adminPermissions() throws Exception {
        mvc.perform(getJson("/api/users").with(as(admin))).andExpect(status().isOk());
        mvc.perform(getJson("/api/companies").with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERADMIN: gestiona empresas, pero no trabaja con el catálogo (no tiene empresa): 409")
    void superAdminHasNoCompany() throws Exception {
        mvc.perform(getJson("/api/companies").with(as(superAdmin))).andExpect(status().isOk());
        mvc.perform(getJson("/api/categories").with(as(superAdmin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esta accion es de una empresa concreta: entra con un usuario de esa empresa"));
    }

    // ----- Aislamiento entre empresas -----

    @Test
    @DisplayName("los datos de otra empresa no existen para ti (404), ni para leer ni para tocar")
    void otherCompanyDataIsInvisible() throws Exception {
        mvc.perform(getJson("/api/categories/{id}", barCategory.getId()).with(as(admin))).andExpect(status().isNotFound());
        mvc.perform(putJson("/api/categories/{id}", CATEGORY, barCategory.getId()).with(as(admin))).andExpect(status().isNotFound());
        mvc.perform(deleteRequest("/api/categories/{id}", barCategory.getId()).with(as(admin))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("los listados solo traen datos de la propia empresa")
    void listsAreFiltered() throws Exception {
        category(forn, "Pans");
        mvc.perform(getJson("/api/categories").with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pans"));
    }

    // ----- CORS -----

    @Test
    @DisplayName("CORS: el frontend (localhost:4200) puede llamar a la API; otro origen no")
    void cors() throws Exception {
        mvc.perform(options("/api/categories")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
        mvc.perform(options("/api/categories")
                        .header(HttpHeaders.ORIGIN, "http://malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
