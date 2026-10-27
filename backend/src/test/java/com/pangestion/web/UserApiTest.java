package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestion de usuarios: SUPERADMIN (todos) y ADMIN (los de su empresa). /api/users. */
@DisplayName("API · Usuarios")
class UserApiTest extends ApiTestSupport {

    private Company forn;
    private Company bar;
    private User superAdmin;
    private User admin;
    private User gestor;
    private User barAdmin;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        bar = company("Bar");
        superAdmin = user(null, Role.SUPERADMIN);
        admin = user(forn, Role.ADMIN);
        gestor = user(forn, Role.GESTOR);
        barAdmin = user(bar, Role.ADMIN);
    }

    private static String body(String email, String password, String role, boolean active, Long companyId) {
        return """
                {"name": "Nuevo Usuario", "email": "%s", "password": %s, "role": "%s", "active": %s, "companyId": %s}
                """.formatted(email, password == null ? "null" : "\"" + password + "\"", role, active, companyId);
    }

    @Test
    @DisplayName("listar: el SUPERADMIN ve todos; un ADMIN solo los de su empresa")
    void list() throws Exception {
        mvc.perform(getJson("/api/users").with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(getJson("/api/users").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("ver un usuario de otra empresa siendo ADMIN: 404")
    void otherCompanyUser() throws Exception {
        mvc.perform(getJson("/api/users/{id}", barAdmin.getId()).with(as(admin))).andExpect(status().isNotFound());
        mvc.perform(getJson("/api/users/{id}", barAdmin.getId()).with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Bar"));
    }

    @Test
    @DisplayName("crear (ADMIN): el usuario queda en su empresa aunque pida otra, y puede entrar")
    void adminCreates() throws Exception {
        mvc.perform(postJson("/api/users", body("Nuevo@Test.es", "clave123", "GESTOR", true, bar.getId())).with(as(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nuevo@test.es"))
                .andExpect(jsonPath("$.companyId").value(forn.getId()))
                .andExpect(jsonPath("$.role").value("GESTOR"));

        mvc.perform(postJson("/api/auth/login", """
                {"email": "nuevo@test.es", "password": "clave123"}
                """)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("crear (SUPERADMIN): elige la empresa; un SUPERADMIN nuevo queda sin empresa")
    void superAdminCreates() throws Exception {
        mvc.perform(postJson("/api/users", body("admin-bar@test.es", "clave123", "ADMIN", true, bar.getId())).with(as(superAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(bar.getId()));
        mvc.perform(postJson("/api/users", body("super2@test.es", "clave123", "SUPERADMIN", true, forn.getId())).with(as(superAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").doesNotExist());
    }

    @Test
    @DisplayName("crear: reglas de negocio (email repetido, sin contraseña, ADMIN creando SUPERADMIN, sin empresa): 409")
    void createRules() throws Exception {
        mvc.perform(postJson("/api/users", body(gestor.getEmail().toUpperCase(), "clave123", "GESTOR", true, null)).with(as(admin)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/users", body("a@test.es", null, "GESTOR", true, null)).with(as(admin)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/users", body("b@test.es", "clave123", "SUPERADMIN", true, null)).with(as(admin)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/users", body("c@test.es", "clave123", "ADMIN", true, null)).with(as(superAdmin)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/users", body("d@test.es", "clave123", "ADMIN", true, 999_999L)).with(as(superAdmin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crear: datos no válidos (email, contraseña corta, sin rol): 400")
    void createValidation() throws Exception {
        mvc.perform(postJson("/api/users", """
                        {"name": "", "email": "no-es-email", "password": "123", "role": null, "active": true}
                        """).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    @DisplayName("editar: sin contraseña mantiene la actual; con contraseña la cambia")
    void updatePassword() throws Exception {
        mvc.perform(putJson("/api/users/{id}", body(gestor.getEmail(), null, "GESTOR", true, null), gestor.getId()).with(as(admin)))
                .andExpect(status().isOk());
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(gestor.getId()).orElseThrow().getPassword())).isTrue();

        mvc.perform(putJson("/api/users/{id}", body(gestor.getEmail(), "nueva-clave", "VISOR", true, null), gestor.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VISOR"));
        assertThat(passwordEncoder.matches("nueva-clave", userRepository.findById(gestor.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    @DisplayName("editar: no puedes quitarte el rol de ADMIN a ti mismo: 409")
    void cannotDemoteYourself() throws Exception {
        user(forn, Role.ADMIN); // aunque haya otro admin
        mvc.perform(putJson("/api/users/{id}", body(admin.getEmail(), null, "GESTOR", true, null), admin.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puedes cambiarte el rol ni desactivarte a ti mismo"));
    }

    @Test
    @DisplayName("editar: la empresa no se puede quedar sin ADMIN activo: 409")
    void lastAdmin() throws Exception {
        mvc.perform(putJson("/api/users/{id}", body(barAdmin.getEmail(), null, "ADMIN", false, bar.getId()), barAdmin.getId())
                        .with(as(superAdmin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La empresa debe conservar al menos un administrador activo"));
    }

    @Test
    @DisplayName("eliminar: 204; a ti mismo o al último ADMIN: 409")
    void delete() throws Exception {
        mvc.perform(deleteRequest("/api/users/{id}", gestor.getId()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(userRepository.findById(gestor.getId())).isEmpty();

        mvc.perform(deleteRequest("/api/users/{id}", admin.getId()).with(as(admin))).andExpect(status().isConflict());
        mvc.perform(deleteRequest("/api/users/{id}", barAdmin.getId()).with(as(superAdmin))).andExpect(status().isConflict());
    }
}
