package com.pangestion.web;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Ingredient;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Productos del catalogo: /api/products (las imagenes van en ProductImageApiTest). */
@DisplayName("API · Productos")
class ProductApiTest extends ApiTestSupport {

    private Company forn;
    private User gestor;
    private Category pans;
    private Category coques;
    private Ingredient nous;
    private Product barra;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        gestor = user(forn, Role.GESTOR);
        pans = category(forn, "Pans");
        coques = category(forn, "Coques");
        nous = ingredient(forn, "Nous", "Frutos de cáscara");
        barra = product(pans, "Barra", "1.10");
    }

    private String body(String name, String price, Long categoryId, String color, String ingredientIds) {
        return """
                {"name": "%s", "abbreviation": " ", "description": "Recién hecha", "color": %s,
                 "price": %s, "active": true, "categoryId": %d, "ingredientIds": [%s]}
                """.formatted(name, color == null ? "null" : "\"" + color + "\"", price, categoryId, ingredientIds);
    }

    @Test
    @DisplayName("listar y filtrar por texto, categoría y activos")
    void search() throws Exception {
        Product coca = product(coques, "Coca de nous", "6.00", nous);
        coca.setActive(false);

        mvc.perform(getJson("/api/products").with(as(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(getJson("/api/products?search=COCA").with(as(gestor)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Coca de nous"));
        mvc.perform(getJson("/api/products?categoryId=" + pans.getId()).with(as(gestor)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Barra"));
        mvc.perform(getJson("/api/products?active=false").with(as(gestor)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    @DisplayName("ver uno: con su categoría, color que se pinta e ingredientes")
    void findById() throws Exception {
        Product coca = product(coques, "Coca de nous", "6.00", nous);

        mvc.perform(getJson("/api/products/{id}", coca.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Coques"))
                .andExpect(jsonPath("$.color").doesNotExist())
                .andExpect(jsonPath("$.displayColor").value("#F59E0B"))
                .andExpect(jsonPath("$.ingredients[0].name").value("Nous"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        mvc.perform(getJson("/api/products/{id}", 999_999).with(as(gestor))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crear: 201, al final de su categoría, color en mayúsculas, abreviatura vacía = null, sin ingredientes repetidos")
    void create() throws Exception {
        mvc.perform(postJson("/api/products",
                        body("Pa de llenya", "2.10", pans.getId(), "#ff0000", nous.getId() + "," + nous.getId())).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.color").value("#FF0000"))
                .andExpect(jsonPath("$.displayColor").value("#FF0000"))
                .andExpect(jsonPath("$.abbreviation").doesNotExist())
                .andExpect(jsonPath("$.price").value(2.10))
                .andExpect(jsonPath("$.ingredients.length()").value(1));
    }

    @Test
    @DisplayName("crear: precio negativo, sin categoría o color mal escrito: 400")
    void createValidation() throws Exception {
        mvc.perform(postJson("/api/products", """
                        {"name": "", "price": -1, "active": true, "categoryId": null, "color": "rojo"}
                        """).with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists())
                .andExpect(jsonPath("$.fieldErrors.color").exists());
    }

    @Test
    @DisplayName("crear con una categoría o un ingrediente de otra empresa: 404")
    void createWithForeignReferences() throws Exception {
        Company bar = company("Bar");
        Category pizzas = category(bar, "Pizzas");
        Ingredient cebolla = ingredient(bar, "Cebolla", null);

        mvc.perform(postJson("/api/products", body("X", "1", pizzas.getId(), null, "")).with(as(gestor)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/products", body("X", "1", pans.getId(), null, cebolla.getId().toString())).with(as(gestor)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("editar: al cambiar de categoría pasa al final de la nueva")
    void updateChangesCategory() throws Exception {
        product(coques, "Coca de llanda", "5.00");
        product(coques, "Coca de nous", "6.00");

        mvc.perform(putJson("/api/products/{id}", body("Barra", "1.20", coques.getId(), null, ""), barra.getId()).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(coques.getId()))
                .andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.price").value(1.20));
    }

    @Test
    @DisplayName("activar / desactivar sin borrarlo")
    void setActive() throws Exception {
        mvc.perform(patchJson("/api/products/{id}/active", "{\"active\": false}", barra.getId()).with(as(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patchJson("/api/products/{id}/active", "{\"active\": true}", barra.getId()).with(as(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("ordenar dentro de una categoría: 204; mezclando categorías: 409")
    void reorder() throws Exception {
        Product llenya = product(pans, "Pa de llenya", "2.10");

        mvc.perform(putJson("/api/products/order", "{\"ids\": [%d, %d]}".formatted(llenya.getId(), barra.getId())).with(as(gestor)))
                .andExpect(status().isNoContent());
        assertThat(productRepository.findById(llenya.getId()).orElseThrow().getSortOrder()).isZero();
        assertThat(productRepository.findById(barra.getId()).orElseThrow().getSortOrder()).isEqualTo(1);

        Product coca = product(coques, "Coca", "5.00");
        mvc.perform(putJson("/api/products/order", "{\"ids\": [%d, %d]}".formatted(coca.getId(), barra.getId())).with(as(gestor)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("eliminar: 204; si aparece en pedidos: 409 (hay que desactivarlo)")
    void delete() throws Exception {
        order(customer(forn, "Lola", "lola@correo.es"), TOMORROW, "09:00", OrderStatus.PENDIENTE, barra, 1);
        mvc.perform(deleteRequest("/api/products/{id}", barra.getId()).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El producto aparece en pedidos: desactivalo en lugar de eliminarlo"));

        Product unused = product(pans, "Sense pedidos", "1.00");
        mvc.perform(deleteRequest("/api/products/{id}", unused.getId()).with(as(gestor))).andExpect(status().isNoContent());
        assertThat(productRepository.findById(unused.getId())).isEmpty();
    }
}
