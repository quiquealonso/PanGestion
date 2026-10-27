package com.pangestion.web;

import com.pangestion.model.BusinessType;
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

/** La empresa del usuario logueado: /api/company y sus franjas. */
@DisplayName("API · Mi empresa")
class MyCompanyApiTest extends ApiTestSupport {

    private Company forn;
    private User admin;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        admin = user(forn, Role.ADMIN);
    }

    @Test
    @DisplayName("ver: cualquier usuario de la empresa ve sus datos")
    void get() throws Exception {
        mvc.perform(getJson("/api/company").with(as(user(forn, Role.VISOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(forn.getId()))
                .andExpect(jsonPath("$.name").value("Forn"));
    }

    @Test
    @DisplayName("editar (ADMIN): cambia horario y datos, pero no el tipo de negocio")
    void updateKeepsType() throws Exception {
        mvc.perform(putJson("/api/company", """
                        {"name": "Forn Nou", "type": "BAR", "taxId": "%s", "address": "Plaça 2",
                         "openingTime": "07:00", "closingTime": "14:00", "slotMinutes": 15, "ordersPerSlot": 8, "active": true}
                        """.formatted(forn.getTaxId())).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Forn Nou"))
                .andExpect(jsonPath("$.slotMinutes").value(15))
                .andExpect(jsonPath("$.type").value("PANADERIA"));
        assertThat(companyRepository.findById(forn.getId()).orElseThrow().getType()).isEqualTo(BusinessType.PANADERIA);
    }

    @Test
    @DisplayName("franjas de un día: horario de la empresa y huecos que quedan")
    void slots() throws Exception {
        var pa = product(category(forn, "Pans"), "Pa", "1.00");
        order(customer(forn, "Lola", "lola@correo.es"), TOMORROW, "08:00", OrderStatus.PENDIENTE, pa, 1);
        order(customer(forn, "Pep", "pep@correo.es"), TOMORROW, "08:00", OrderStatus.PENDIENTE, pa, 1);

        mvc.perform(getJson("/api/company/slots?date=" + TOMORROW).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24)) // de 08:00 a 20:00 cada 30 minutos
                .andExpect(jsonPath("$[0].time").value("08:00"))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].remaining").value(0))
                .andExpect(jsonPath("$[1].available").value(true))
                .andExpect(jsonPath("$[1].remaining").value(2));
    }

    @Test
    @DisplayName("franjas sin fecha o con una fecha mal escrita: 400")
    void slotsBadDate() throws Exception {
        mvc.perform(getJson("/api/company/slots").with(as(admin))).andExpect(status().is4xxClientError());
        mvc.perform(getJson("/api/company/slots?date=mañana").with(as(admin))).andExpect(status().isBadRequest());
    }
}
