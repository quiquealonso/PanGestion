package com.pangestion.web;

import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Formato comun de los errores (GlobalExceptionHandler): status, message, fieldErrors y timestamp. */
@DisplayName("API · Formato de los errores")
class ErrorHandlingApiTest extends ApiTestSupport {

    private User gestor;

    @BeforeEach
    void setUp() {
        gestor = user(company("Forn"), Role.GESTOR);
    }

    @Test
    @DisplayName("404: recurso que no existe, con mensaje claro")
    void notFound() throws Exception {
        mvc.perform(getJson("/api/products/{id}", 424242).with(as(gestor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Producto con id 424242 no encontrado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("400: datos no válidos, con el error de cada campo")
    void validation() throws Exception {
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"\"}").with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Datos no validos"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("400: JSON mal escrito")
    void malformedJson() throws Exception {
        mvc.perform(postJson("/api/ingredients", "{\"name\": ").with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Peticion mal formada"));
    }

    @Test
    @DisplayName("400: un id que no es un número o un valor de enum que no existe")
    void typeMismatch() throws Exception {
        mvc.perform(getJson("/api/products/abc").with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Peticion mal formada"));
        mvc.perform(postJson("/api/users", """
                {"name": "x", "email": "x@test.es", "password": "secreto", "role": "JEFE", "active": true}
                """).with(as(user(gestor.getCompany(), Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400: un campo que no existe en el DTO (JSON estricto) no se ignora en silencio")
    void unknownField() throws Exception {
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"Sal\", \"alergeno\": \"ninguno\"}").with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campo no permitido: alergeno"));
        // Tampoco se pueden colar campos que solo pone el servidor
        mvc.perform(postJson("/api/ingredients", "{\"id\": 99, \"name\": \"Sal\"}").with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campo no permitido: id"));
        assertThat(ingredientRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("400: falta un parámetro obligatorio")
    void missingParameter() throws Exception {
        mvc.perform(getJson("/api/orders/production").with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parametro obligatorio: date"));
    }

    @Test
    @DisplayName("404, 405 y 415 con el mismo formato que el resto de errores")
    void routingErrors() throws Exception {
        mvc.perform(getJson("/api/no-existe").with(as(gestor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ruta no encontrada"));
        mvc.perform(patchJson("/api/categories", "{}").with(as(gestor)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/api/ingredients").contentType(MediaType.TEXT_PLAIN).content("Sal").with(as(gestor)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("409: regla de negocio incumplida, con su explicación")
    void businessRule() throws Exception {
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"Sal\"}").with(as(gestor))).andExpect(status().isCreated());
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"Sal\"}").with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ya existe un ingrediente con el nombre Sal"));
    }
}
