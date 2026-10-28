package com.pangestion.web;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Categorias del catalogo: /api/categories. */
@DisplayName("API · Categorías")
class CategoryApiTest extends ApiTestSupport {

    private Company forn;
    private User gestor;
    private Category pans;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        gestor = user(forn, Role.GESTOR);
        pans = category(forn, "Pans");
    }

    private static String body(String name, String color) {
        return """
                {"name": "%s", "color": "%s", "description": "Descripción"}
                """.formatted(name, color);
    }

    @Test
    @DisplayName("listar: por posición y con el número de productos")
    void list() throws Exception {
        category(forn, "Dolços");
        product(pans, "Barra", "1.10");

        mvc.perform(getJson("/api/categories").with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pans"))
                .andExpect(jsonPath("$[0].productCount").value(1))
                .andExpect(jsonPath("$[1].name").value("Dolços"))
                .andExpect(jsonPath("$[1].sortOrder").value(1));
    }

    @Test
    @DisplayName("ver una: 200; una que no existe: 404")
    void findById() throws Exception {
        mvc.perform(getJson("/api/categories/{id}", pans.getId()).with(as(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Pans"));
        mvc.perform(getJson("/api/categories/{id}", 999_999).with(as(gestor))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crear: 201, va al final y el color se guarda en mayúsculas")
    void create() throws Exception {
        mvc.perform(postJson("/api/categories", body("  Coques ", "#abcdef")).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesRegex("http://localhost/api/categories/\\d+")))
                .andExpect(jsonPath("$.name").value("Coques"))
                .andExpect(jsonPath("$.color").value("#ABCDEF"))
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.productCount").value(0));
    }

    @Test
    @DisplayName("crear: nombre repetido en la empresa: 409; en otra empresa sí se puede")
    void createDuplicate() throws Exception {
        mvc.perform(postJson("/api/categories", body("PANS", "#000000")).with(as(gestor))).andExpect(status().isConflict());

        User barGestor = user(company("Bar"), Role.GESTOR);
        mvc.perform(postJson("/api/categories", body("Pans", "#000000")).with(as(barGestor))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("crear: color que no es hexadecimal o sin nombre: 400")
    void createValidation() throws Exception {
        mvc.perform(postJson("/api/categories", body("", "rojo")).with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.color").value("debe ser un color hexadecimal, por ejemplo #F59E0B"));
    }

    @Test
    @DisplayName("editar: cambia nombre y color; mantener su propio nombre no es un duplicado")
    void update() throws Exception {
        mvc.perform(putJson("/api/categories/{id}", body("Pans", "#111111"), pans.getId()).with(as(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value("#111111"));

        category(forn, "Dolços");
        mvc.perform(putJson("/api/categories/{id}", body("dolços", "#111111"), pans.getId()).with(as(gestor)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ordenar: cada categoría toma la posición que ocupa en la lista")
    void reorder() throws Exception {
        Category dolcos = category(forn, "Dolços");
        Category coques = category(forn, "Coques");

        mvc.perform(putJson("/api/categories/order", """
                        {"ids": [%d, %d, %d]}
                        """.formatted(coques.getId(), pans.getId(), dolcos.getId())).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Coques"))
                .andExpect(jsonPath("$[1].name").value("Pans"))
                .andExpect(jsonPath("$[2].name").value("Dolços"));
    }

    @Test
    @DisplayName("ordenar con lista vacía: 400; con una categoría de otra empresa: 404")
    void reorderInvalid() throws Exception {
        mvc.perform(putJson("/api/categories/order", "{\"ids\": []}").with(as(gestor))).andExpect(status().isBadRequest());

        Category other = category(company("Bar"), "Pizzas");
        mvc.perform(putJson("/api/categories/order", "{\"ids\": [%d]}".formatted(other.getId())).with(as(gestor)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("eliminar una categoría vacía: 204; con productos: 409")
    void delete() throws Exception {
        product(pans, "Barra", "1.10");
        mvc.perform(deleteRequest("/api/categories/{id}", pans.getId()).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede eliminar una categoria que tiene productos"));

        Category empty = category(forn, "Vacía");
        mvc.perform(deleteRequest("/api/categories/{id}", empty.getId()).with(as(gestor))).andExpect(status().isNoContent());
        assertThat(categoryRepository.findById(empty.getId())).isEmpty();
    }

    @Test
    @DisplayName("estación de la categoría: Bebidas a barra pasa todos sus productos a barra, y los nuevos nacen en barra")
    void categoryStation() throws Exception {
        Category bebidas = category(forn, "Bebidas");
        product(bebidas, "Caña", "1.80");
        mvc.perform(putJson("/api/categories/{id}", """
                        {"name": "Bebidas", "color": "#0EA5E9", "description": null, "station": "BARRA"}
                        """, bebidas.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.station").value("BARRA"));
        mvc.perform(getJson("/api/products?categoryId={id}", bebidas.getId()).with(as(gestor)))
                .andExpect(jsonPath("$[0].station").value("BARRA"));

        // Aunque el producto pida cocina, manda la categoría
        mvc.perform(postJson("/api/products", """
                        {"name": "Refresco", "price": 2.20, "active": true, "categoryId": %d, "station": "COCINA"}
                        """.formatted(bebidas.getId())).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.station").value("BARRA"));
    }

    @Test
    @DisplayName("orden en cocina: aparte del orden del catálogo; con una categoría de otra empresa: 404")
    void kitchenOrder() throws Exception {
        Category entrantes = category(forn, "Entrantes");
        mvc.perform(putJson("/api/categories/kitchen-order", "{\"ids\": [%d, %d]}".formatted(entrantes.getId(), pans.getId()))
                        .with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pans"))
                .andExpect(jsonPath("$[0].kitchenOrder").value(1))
                .andExpect(jsonPath("$[1].name").value("Entrantes"))
                .andExpect(jsonPath("$[1].kitchenOrder").value(0));
        Category other = category(company("Otro"), "Otra");
        mvc.perform(putJson("/api/categories/kitchen-order", "{\"ids\": [%d]}".formatted(other.getId())).with(as(gestor)))
                .andExpect(status().isNotFound());
    }
}
