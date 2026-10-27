package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantalla de inicio: indicadores y grafica semanal. /api/dashboard. */
@DisplayName("API · Inicio (dashboard)")
class DashboardApiTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.now();

    private Company forn;
    private User visor;
    private Customer lola;
    private Product pa;
    private Product coca;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        visor = user(forn, Role.VISOR);
        lola = customer(forn, "Lola", "lola@correo.es");
        var pans = category(forn, "Pans");
        pa = product(pans, "Pa", "1.00");
        coca = product(pans, "Coca", "5.00");
        product(pans, "Desactivado", "1.00").setActive(false);
    }

    @Test
    @DisplayName("resumen: pedidos por estado, recogidas de hoy, clientes, productos activos, facturación y más vendidos")
    void summary() throws Exception {
        order(lola, TODAY, "09:00", OrderStatus.PENDIENTE, pa, 4);
        order(lola, TODAY, "09:30", OrderStatus.EN_PROCESO, coca, 1);
        order(lola, TODAY, "10:00", OrderStatus.LISTO, pa, 1);
        order(lola, TODAY, "10:00", OrderStatus.CANCELADO, coca, 20);
        // Otra empresa: no cuenta
        Company bar = company("Bar");
        Product pizza = product(category(bar, "Pizzas"), "Pizza", "8.00");
        order(customer(bar, "Pep", "pep@correo.es"), TODAY, "09:00", OrderStatus.PENDIENTE, pizza, 50);

        mvc.perform(getJson("/api/dashboard").with(as(visor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOrders").value(1))
                .andExpect(jsonPath("$.inProcessOrders").value(1))
                .andExpect(jsonPath("$.readyOrders").value(1))
                .andExpect(jsonPath("$.todayOrders").value(3))
                .andExpect(jsonPath("$.customers").value(1))
                .andExpect(jsonPath("$.activeProducts").value(2))
                .andExpect(jsonPath("$.monthRevenue").value(10.00))
                .andExpect(jsonPath("$.monthOrders").value(3))
                .andExpect(jsonPath("$.averageTicket").value(3.33))
                .andExpect(jsonPath("$.topProducts[0].name").value("Pa"))
                .andExpect(jsonPath("$.topProducts[0].units").value(5))
                .andExpect(jsonPath("$.topProducts[1].name").value("Coca"))
                .andExpect(jsonPath("$.topProducts[1].units").value(1));
    }

    @Test
    @DisplayName("resumen de una empresa sin pedidos: todo a cero (sin dividir entre cero)")
    void emptySummary() throws Exception {
        mvc.perform(getJson("/api/dashboard").with(as(visor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthOrders").value(0))
                .andExpect(jsonPath("$.monthRevenue").value(0))
                .andExpect(jsonPath("$.averageTicket").value(0))
                .andExpect(jsonPath("$.topProducts.length()").value(0));
    }

    @Test
    @DisplayName("semana: unidades de lunes a domingo de la semana pedida y de la anterior, sin cancelados")
    void weeklySales() throws Exception {
        LocalDate monday = LocalDate.of(2030, 1, 7);
        order(lola, monday, "09:00", OrderStatus.ENTREGADO, pa, 3, coca, 1);
        order(lola, monday.plusDays(5), "09:00", OrderStatus.PENDIENTE, pa, 2);
        order(lola, monday.plusDays(5), "09:30", OrderStatus.CANCELADO, pa, 50);
        order(lola, monday.minusWeeks(1), "09:00", OrderStatus.ENTREGADO, pa, 7);

        mvc.perform(getJson("/api/dashboard/weekly-sales?date={d}", monday.plusDays(2)).with(as(visor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2030-01-07"))
                .andExpect(jsonPath("$.current.length()").value(7))
                .andExpect(jsonPath("$.current[0]").value(4))
                .andExpect(jsonPath("$.current[5]").value(2))
                .andExpect(jsonPath("$.current[6]").value(0))
                .andExpect(jsonPath("$.previous[0]").value(7));
    }

    @Test
    @DisplayName("semana sin fecha: la semana actual")
    void weeklySalesDefault() throws Exception {
        mvc.perform(getJson("/api/dashboard/weekly-sales").with(as(visor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(TODAY.with(DayOfWeek.MONDAY).toString()));
    }
}
