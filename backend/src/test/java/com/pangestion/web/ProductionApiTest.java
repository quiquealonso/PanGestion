package com.pangestion.web;

import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Produccion del dia (cocina/obrador) y "Pasar a en proceso": /api/orders/production y /api/orders/start-day. */
@DisplayName("API · Producción del día")
class ProductionApiTest extends ApiTestSupport {

    private User visor;
    private User gestor;
    private Customer lola;
    private Product pizza;
    private Product bravas;

    @BeforeEach
    void setUp() {
        Company bar = company("Bar");
        visor = user(bar, Role.VISOR);
        gestor = user(bar, Role.GESTOR);
        lola = customer(bar, "Lola", "lola@correo.es");
        pizza = product(category(bar, "Pizzas"), "Vegetal", "8.00", ingredient(bar, "Cebolla", null));
        bravas = product(category(bar, "Entrantes"), "Bravas", "4.50");
    }

    /** Pedido con una pizza "sin cebolla". */
    private Order orderWithoutOnion(String time, OrderStatus status, int quantity) {
        Order order = order(lola, TOMORROW, time, status);
        order.addLine(pizza, quantity, List.of("Cebolla"));
        return order;
    }

    @Test
    @DisplayName("suma por producto + ingredientes quitados + hora: 'Vegetal' y 'Vegetal sin cebolla' van en filas distintas")
    void groupsRows() throws Exception {
        order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, pizza, 2);
        order(lola, TOMORROW, "13:00", OrderStatus.EN_PROCESO, pizza, 3, bravas, 1);
        orderWithoutOnion("13:00", OrderStatus.PENDIENTE, 1);
        order(lola, TOMORROW, "14:00", OrderStatus.LISTO, pizza, 1);

        mvc.perform(getJson("/api/orders/production?date=" + TOMORROW).with(as(visor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].productName").value("Vegetal"))
                .andExpect(jsonPath("$[0].categoryName").value("Pizzas"))
                .andExpect(jsonPath("$[0].pickupTime").value("13:00"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].removedIngredients.length()").value(0))
                .andExpect(jsonPath("$[1].pickupTime").value("14:00"))
                .andExpect(jsonPath("$[2].removedIngredients[0]").value("Cebolla"))
                .andExpect(jsonPath("$[2].quantity").value(1))
                .andExpect(jsonPath("$[3].productName").value("Bravas"));
    }

    @Test
    @DisplayName("por defecto solo lo que queda por entregar; pending=false incluye lo entregado; los cancelados nunca")
    void pendingFilter() throws Exception {
        order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, bravas, 1);
        order(lola, TOMORROW, "13:00", OrderStatus.ENTREGADO, bravas, 2);
        order(lola, TOMORROW, "13:00", OrderStatus.CANCELADO, bravas, 10);

        mvc.perform(getJson("/api/orders/production?date=" + TOMORROW).with(as(visor)))
                .andExpect(jsonPath("$[0].quantity").value(1));
        mvc.perform(getJson("/api/orders/production?date=" + TOMORROW + "&pending=false").with(as(visor)))
                .andExpect(jsonPath("$[0].quantity").value(3));
    }

    @Test
    @DisplayName("un día sin pedidos devuelve una lista vacía; sin fecha: error 4xx")
    void emptyDay() throws Exception {
        mvc.perform(getJson("/api/orders/production?date=" + TOMORROW.plusDays(5)).with(as(visor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(getJson("/api/orders/production").with(as(visor))).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("pasar a 'en proceso': solo los PENDIENTES de ese día; devuelve cuántos ha cambiado")
    void startDay() throws Exception {
        Order a = order(lola, TOMORROW, "13:00", OrderStatus.PENDIENTE, bravas, 1);
        Order b = order(lola, TOMORROW, "14:00", OrderStatus.PENDIENTE, bravas, 1);
        Order ready = order(lola, TOMORROW, "14:00", OrderStatus.LISTO, bravas, 1);
        Order otherDay = order(lola, TOMORROW.plusDays(1), "14:00", OrderStatus.PENDIENTE, bravas, 1);

        mvc.perform(postJson("/api/orders/start-day?date=" + TOMORROW, "").with(as(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        assertThat(List.of(a, b)).allMatch(o -> o.getStatus() == OrderStatus.EN_PROCESO);
        assertThat(ready.getStatus()).isEqualTo(OrderStatus.LISTO);
        assertThat(otherDay.getStatus()).isEqualTo(OrderStatus.PENDIENTE);

        mvc.perform(postJson("/api/orders/start-day?date=" + TOMORROW, "").with(as(gestor)))
                .andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    @DisplayName("los pedidos de otra empresa no salen en tu producción")
    void otherCompany() throws Exception {
        Company forn = company("Forn");
        Product pa = product(category(forn, "Pans"), "Pa", "1.00");
        order(customer(forn, "Pep", "pep@correo.es"), TOMORROW, "09:00", OrderStatus.PENDIENTE, pa, 9);

        mvc.perform(getJson("/api/orders/production?date=" + TOMORROW).with(as(visor)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
