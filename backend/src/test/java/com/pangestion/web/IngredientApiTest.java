package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Ingredient;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ingredientes (de que esta hecho cada producto y sus alergenos): /api/ingredients. */
@DisplayName("API · Ingredientes")
class IngredientApiTest extends ApiTestSupport {

    private Company forn;
    private User gestor;
    private Ingredient nous;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        gestor = user(forn, Role.GESTOR);
        nous = ingredient(forn, "Nous", "Frutos de cáscara");
    }

    @Test
    @DisplayName("listar: por nombre, con su alérgeno y en cuántos productos se usa")
    void list() throws Exception {
        ingredient(forn, "Ametla", null);
        product(category(forn, "Coques"), "Coca de nous", "6.00", nous);

        mvc.perform(getJson("/api/ingredients").with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ametla"))
                .andExpect(jsonPath("$[1].name").value("Nous"))
                .andExpect(jsonPath("$[1].allergen").value("Frutos de cáscara"))
                .andExpect(jsonPath("$[1].productCount").value(1));
    }

    @Test
    @DisplayName("ver uno: 200; de otra empresa: 404")
    void findById() throws Exception {
        mvc.perform(getJson("/api/ingredients/{id}", nous.getId()).with(as(gestor))).andExpect(status().isOk());
        Ingredient other = ingredient(company("Bar"), "Cebolla", null);
        mvc.perform(getJson("/api/ingredients/{id}", other.getId()).with(as(gestor))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crear: 201; un alérgeno en blanco se guarda como sin alérgeno")
    void create() throws Exception {
        mvc.perform(postJson("/api/ingredients", """
                        {"name": " Cebolla ", "allergen": "  "}
                        """).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cebolla"))
                .andExpect(jsonPath("$.allergen").doesNotExist());
    }

    @Test
    @DisplayName("crear: nombre repetido: 409; sin nombre: 400")
    void createInvalid() throws Exception {
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"NOUS\"}").with(as(gestor))).andExpect(status().isConflict());
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"\"}").with(as(gestor)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("editar: cambia nombre y alérgeno; el nombre de otro ingrediente: 409")
    void update() throws Exception {
        mvc.perform(putJson("/api/ingredients/{id}", """
                        {"name": "Nueces", "allergen": "Frutos secos"}
                        """, nous.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nueces"))
                .andExpect(jsonPath("$.allergen").value("Frutos secos"));

        ingredient(forn, "Ametla", null);
        mvc.perform(putJson("/api/ingredients/{id}", "{\"name\": \"ametla\"}", nous.getId()).with(as(gestor)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("eliminar uno que no usa nadie: 204; uno que llevan productos: 409")
    void delete() throws Exception {
        product(category(forn, "Coques"), "Coca de nous", "6.00", nous);
        mvc.perform(deleteRequest("/api/ingredients/{id}", nous.getId()).with(as(gestor))).andExpect(status().isConflict());

        Ingredient free = ingredient(forn, "Sucre", null);
        mvc.perform(deleteRequest("/api/ingredients/{id}", free.getId()).with(as(gestor))).andExpect(status().isNoContent());
        assertThat(ingredientRepository.findById(free.getId())).isEmpty();
    }

    @Test
    @DisplayName("precio de extra: se guarda y se puede quitar (null = no es extra); negativo: 400")
    void extraPrice() throws Exception {
        String json = mvc.perform(postJson("/api/ingredients", "{\"name\": \"Huevo\", \"allergen\": \"Huevo\", \"extraPrice\": 1.00}")
                        .with(as(user(company("Bar"), Role.GESTOR))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extraPrice").value(1.00))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(postJson("/api/ingredients", "{\"name\": \"Bacon\", \"allergen\": null, \"extraPrice\": -1}")
                        .with(as(user(company("Bar 2"), Role.GESTOR))))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(json).contains("\"extraPrice\":1.0");
    }
}
