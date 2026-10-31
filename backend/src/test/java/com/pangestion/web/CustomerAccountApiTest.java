package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.CustomerAccount;
import com.pangestion.model.Role;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cuentas de cliente de la web publica, en un servidor sin SMTP (la cuenta se activa al registrarse). */
@DisplayName("API · Cuentas de cliente")
class CustomerAccountApiTest extends ApiTestSupport {

    private static final String REGISTER = """
            {"name": "Lola", "email": "Lola@Correo.es", "password": "unaclave123", "phone": "600 000 000"}
            """;

    @Test
    @DisplayName("registro sin SMTP: 201, cuenta activa con sesión iniciada; contraseña con hash y teléfono cifrado")
    void registerWithoutSmtp() throws Exception {
        String token = com.jayway.jsonpath.JsonPath.read(mvc.perform(postJson("/api/public/account/register", REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(false))
                .andExpect(jsonPath("$.session.account.email").value("lola@correo.es"))
                .andReturn().getResponse().getContentAsString(), "$.session.token");

        CustomerAccount saved = customerAccountRepository.findByEmailIgnoreCase("lola@correo.es").orElseThrow();
        assertThat(saved.isVerified()).isTrue();
        assertThat(saved.getPassword()).startsWith("$2").doesNotContain("unaclave123");

        mvc.perform(getJson("/api/public/account/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lola"))
                .andExpect(jsonPath("$.phone").value("600 000 000"));
    }

    @Test
    @DisplayName("registro: email repetido (sin SMTP) 409; contraseña corta o email mal escrito 400")
    void registerRules() throws Exception {
        account("Lola", "lola@correo.es", null);
        mvc.perform(postJson("/api/public/account/register", REGISTER)).andExpect(status().isConflict());
        mvc.perform(postJson("/api/public/account/register", """
                        {"name": "A", "email": "no-es-email", "password": "corta"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("login: 200 con la contraseña buena, 401 con la mala o con un email que no existe")
    void login() throws Exception {
        account("Lola", "lola@correo.es", null);
        mvc.perform(postJson("/api/public/account/login", "{\"email\": \"LOLA@correo.es\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.account.name").value("Lola"));
        mvc.perform(postJson("/api/public/account/login", "{\"email\": \"lola@correo.es\", \"password\": \"otra-clave\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/public/account/login", "{\"email\": \"nadie@correo.es\", \"password\": \"otra-clave\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un cliente no puede usar la API de gestión (ni leer): 403")
    void customerCannotUseStaffApi() throws Exception {
        Company bar = company("Bar");
        user(bar, Role.ADMIN);
        CustomerAccount lola = account("Lola", "lola@correo.es", null);
        for (String url : new String[]{"/api/orders", "/api/products", "/api/customers", "/api/auth/me", "/api/dashboard", "/api/users"}) {
            mvc.perform(getJson(url).with(asCustomer(lola))).andExpect(status().isForbidden());
        }
        mvc.perform(postJson("/api/categories", "{\"name\": \"X\"}").with(asCustomer(lola))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/me sin token: 401; con el token de un usuario de gestión: 403")
    void meNeedsCustomerToken() throws Exception {
        mvc.perform(getJson("/api/public/account/me")).andExpect(status().isUnauthorized());
        mvc.perform(getJson("/api/public/account/me").with(as(user(company("Bar"), Role.ADMIN)))).andExpect(status().isForbidden());
    }
}
