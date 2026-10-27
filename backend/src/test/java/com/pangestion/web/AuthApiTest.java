package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login y "quien soy": POST /api/auth/login y GET /api/auth/me. */
@DisplayName("API · Autenticación")
class AuthApiTest extends ApiTestSupport {

    private Company company;
    private User admin;

    @BeforeEach
    void setUp() {
        company = company("Panadería Login");
        admin = user(company, Role.ADMIN);
    }

    private String login(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    @Test
    @DisplayName("login correcto: devuelve token, caducidad y datos del usuario")
    void loginOk() throws Exception {
        mvc.perform(postJson("/api/auth/login", login(admin.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresInMinutes").value(480))
                .andExpect(jsonPath("$.user.email").value(admin.getEmail()))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.companyId").value(company.getId()));
    }

    @Test
    @DisplayName("login: el email no distingue mayúsculas ni espacios")
    void loginEmailCaseInsensitive() throws Exception {
        mvc.perform(postJson("/api/auth/login", login("  " + admin.getEmail().toUpperCase() + " ", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("login: guarda la fecha del último acceso")
    void loginSavesLastLogin() throws Exception {
        assertThat(admin.getLastLogin()).isNull();
        mvc.perform(postJson("/api/auth/login", login(admin.getEmail(), PASSWORD))).andExpect(status().isOk());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getLastLogin()).isNotNull();
    }

    @Test
    @DisplayName("login con contraseña incorrecta: 401")
    void loginWrongPassword() throws Exception {
        mvc.perform(postJson("/api/auth/login", login(admin.getEmail(), "otra-cosa")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email o contrasena incorrectos"));
    }

    @Test
    @DisplayName("login con un email que no existe: 401")
    void loginUnknownEmail() throws Exception {
        mvc.perform(postJson("/api/auth/login", login("nadie@test.es", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login de un usuario desactivado: 401")
    void loginInactiveUser() throws Exception {
        admin.setActive(false);
        mvc.perform(postJson("/api/auth/login", login(admin.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login sin email ni contraseña: 400 con el error de cada campo")
    void loginValidation() throws Exception {
        mvc.perform(postJson("/api/auth/login", login("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("el token del login sirve para llamar a la API")
    void tokenFromLoginWorks() throws Exception {
        String response = mvc.perform(postJson("/api/auth/login", login(admin.getEmail(), PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        String token = response.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mvc.perform(getJson("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(admin.getEmail()));
    }

    @Test
    @DisplayName("/me devuelve el usuario del token")
    void me() throws Exception {
        mvc.perform(getJson("/api/auth/me").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.name").value(admin.getName()))
                .andExpect(jsonPath("$.companyName").value("Panadería Login"));
    }

    @Test
    @DisplayName("/me sin token: 401")
    void meWithoutToken() throws Exception {
        mvc.perform(getJson("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me de un usuario desactivado después de recibir su token: 401")
    void meInactiveUser() throws Exception {
        admin.setActive(false);
        mvc.perform(getJson("/api/auth/me").with(as(admin))).andExpect(status().isUnauthorized());
    }
}
