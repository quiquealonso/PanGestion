package com.pangestion.web;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Plano de la sala de un bar: /api/dining (zonas y mesas). */
@DisplayName("API · Sala y mesas")
class DiningApiTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Company bar;
    private User gestor;

    @BeforeEach
    void setUp() {
        bar = company("Bar");
        bar.setType(BusinessType.BAR);
        gestor = user(bar, Role.GESTOR);
    }

    private static String area(String name, int width, int height) {
        return """
                {"name": "%s", "width": %d, "height": %d}
                """.formatted(name, width, height);
    }

    private static String table(String name, String shape, int x, int y, int width, int height) {
        return """
                {"name": "%s", "seats": 4, "shape": "%s", "x": %d, "y": %d, "width": %d, "height": %d}
                """.formatted(name, shape, x, y, width, height);
    }

    private long createArea(String name) throws Exception {
        String json = mvc.perform(postJson("/api/dining/areas", area(name, 20, 10)).with(as(gestor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("id").asLong();
    }

    private long createTable(long areaId, String name, int x, int y) throws Exception {
        String json = mvc.perform(postJson("/api/dining/areas/{id}/tables", table(name, "RECT", x, y, 2, 2), areaId).with(as(gestor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("id").asLong();
    }

    @Test
    @DisplayName("crear zona: 201 con Location; aparece en el plano con sus mesas y plazas")
    void createAreaAndTables() throws Exception {
        String location = mvc.perform(postJson("/api/dining/areas", area("Terraza", 20, 10)).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).matches("http://localhost/api/dining/areas/\\d+");
        long terraza = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(postJson("/api/dining/areas/{id}/tables", table("T1", "ROUND", 0, 0, 2, 2), terraza).with(as(gestor)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("http://localhost/api/dining/tables/\\d+")))
                .andExpect(jsonPath("$.areaId").value(terraza))
                .andExpect(jsonPath("$.shape").value("ROUND"));
        createTable(terraza, "T2", 3, 0);

        mvc.perform(getJson("/api/dining/areas").with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Terraza"))
                .andExpect(jsonPath("$[0].seats").value(8))
                .andExpect(jsonPath("$[0].tables.length()").value(2));
    }

    @Test
    @DisplayName("una panadería no tiene plano de mesas: 409")
    void bakeryCannotCreateAreas() throws Exception {
        User baker = user(company("Forn"), Role.GESTOR);
        mvc.perform(postJson("/api/dining/areas", area("Sala", 20, 10)).with(as(baker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El plano de mesas solo esta disponible para bares y restaurantes"));
    }

    @Test
    @DisplayName("mover una mesa encima de otra o fuera de la zona: 409; a un hueco libre: 200")
    void placementRules() throws Exception {
        long sala = createArea("Sala");
        createTable(sala, "1", 0, 0);
        long two = createTable(sala, "2", 4, 0);

        mvc.perform(putJson("/api/dining/tables/{id}", table("2", "RECT", 1, 1, 2, 2), two).with(as(gestor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La mesa 2 se solapa con la mesa 1"));
        mvc.perform(putJson("/api/dining/tables/{id}", table("2", "RECT", 19, 0, 2, 2), two).with(as(gestor)))
                .andExpect(status().isConflict());
        mvc.perform(putJson("/api/dining/tables/{id}", table("2", "RECT", 2, 0, 3, 2), two).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(2))
                .andExpect(jsonPath("$.width").value(3));
    }

    @Test
    @DisplayName("nombre de mesa repetido en la empresa (aunque sea otra zona): 409")
    void duplicateTableName() throws Exception {
        createTable(createArea("Sala"), "1", 0, 0);
        mvc.perform(postJson("/api/dining/areas/{id}/tables", table("1", "RECT", 0, 0, 2, 2), createArea("Terraza")).with(as(gestor)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("encoger la zona dejando una mesa fuera: 409")
    void shrinkAreaWithTableOutside() throws Exception {
        long sala = createArea("Sala");
        createTable(sala, "1", 15, 0);
        mvc.perform(putJson("/api/dining/areas/{id}", area("Sala", 10, 10), sala).with(as(gestor)))
                .andExpect(status().isConflict());
        mvc.perform(putJson("/api/dining/areas/{id}", area("Salón", 18, 10), sala).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Salón"));
    }

    @Test
    @DisplayName("datos inválidos: 400 con el campo; campo desconocido: 400")
    void validation() throws Exception {
        long sala = createArea("Sala");
        mvc.perform(postJson("/api/dining/areas/{id}/tables", table("", "RECT", 0, 0, 0, 2), sala).with(as(gestor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.width").exists());
        mvc.perform(postJson("/api/dining/areas", "{\"name\": \"X\", \"width\": 10, \"height\": 10, \"areaId\": 1}").with(as(gestor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("borrar mesa y zona (con sus mesas): 204; el visor no puede: 403")
    void deleteAndPermissions() throws Exception {
        long sala = createArea("Sala");
        long one = createTable(sala, "1", 0, 0);
        createTable(sala, "2", 4, 0);
        User visor = user(bar, Role.VISOR);

        mvc.perform(delete("/api/dining/tables/{id}", one).with(as(visor))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/dining/tables/{id}", one).with(as(gestor))).andExpect(status().isNoContent());
        mvc.perform(getJson("/api/dining/areas/{id}", sala).with(as(visor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(1));
        mvc.perform(delete("/api/dining/areas/{id}", sala).with(as(gestor))).andExpect(status().isNoContent());
        mvc.perform(getJson("/api/dining/areas").with(as(gestor))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("zonas y mesas de otra empresa: 404")
    void otherCompany() throws Exception {
        long sala = createArea("Sala");
        long one = createTable(sala, "1", 0, 0);
        Company other = company("Otro bar");
        other.setType(BusinessType.BAR);
        User stranger = user(other, Role.GESTOR);

        mvc.perform(getJson("/api/dining/areas/{id}", sala).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(putJson("/api/dining/tables/{id}", table("1", "RECT", 2, 2, 2, 2), one).with(as(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ordenar zonas: cambian las pestañas")
    void reorder() throws Exception {
        long sala = createArea("Sala");
        long terraza = createArea("Terraza");
        mvc.perform(putJson("/api/dining/areas/order", "{\"ids\": [%d, %d]}".formatted(terraza, sala)).with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Terraza"));
    }
}
