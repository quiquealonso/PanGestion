package com.pangestion.repository;

import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Order;
import com.pangestion.model.OrderLine;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.support.RepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Consultas de pedidos: busqueda, aforo de franjas, facturacion, produccion y estadisticas. */
@DisplayName("Repositorio · pedidos")
class OrderRepositoriesTest extends RepositoryTestSupport {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderLineRepository orderLineRepository;

    private Company forn;
    private Customer lola;
    private Customer pep;
    private Product pa;
    private Product coca;
    private Order pending;
    private Order cancelled;
    private Order delivered;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        var pans = category(forn, "Pans", 0);
        pa = product(pans, "Pa", "1.00", 0, true);
        coca = product(pans, "Coca", "5.00", 1, true);
        lola = customer(forn, "Lola Ferrer", "lola@correo.es", null);
        pep = customer(forn, "Pep Soler", "pep@correo.es", null);
        pending = order(lola, MONDAY, "09:00", OrderStatus.PENDIENTE, pa, 3);
        delivered = order(pep, MONDAY, "09:00", OrderStatus.ENTREGADO, coca, 1);
        cancelled = order(pep, MONDAY, "09:00", OrderStatus.CANCELADO, pa, 10);
        order(lola, MONDAY.plusDays(1), "10:00", OrderStatus.LISTO, pa, 2, coca, 2);

        // Otra empresa: nunca debe aparecer
        var bar = company("Bar");
        var pizza = product(category(bar, "Pizzas", 0), "Pizza", "8.00", 0, true);
        order(customer(bar, "Lola Bar", "lola@bar.es", null), MONDAY, "09:00", OrderStatus.PENDIENTE, pizza, 50);
        flushAndClear();
    }

    @Test
    @DisplayName("búsqueda: por cliente o por número, estado y rango de fechas; más recientes primero")
    void search() {
        assertThat(orderRepository.search(forn.getId(), "%", null, null, null)).hasSize(4)
                .first().extracting(Order::getPickupDate).isEqualTo(MONDAY.plusDays(1));
        assertThat(orderRepository.search(forn.getId(), "%lola%", null, null, null)).hasSize(2);
        assertThat(orderRepository.search(forn.getId(), "%" + pending.getId() + "%", null, null, null))
                .extracting(Order::getId).contains(pending.getId());
        assertThat(orderRepository.search(forn.getId(), "%", OrderStatus.CANCELADO, null, null))
                .extracting(Order::getId).containsExactly(cancelled.getId());
        assertThat(orderRepository.search(forn.getId(), "%", null, MONDAY.plusDays(1), null)).hasSize(1);
        assertThat(orderRepository.search(forn.getId(), "%", null, null, MONDAY)).hasSize(3);
    }

    @Test
    @DisplayName("aforo de una franja: los cancelados no cuentan, y se puede excluir el pedido que se edita")
    void countInSlot() {
        Company company = em.find(Company.class, forn.getId());
        assertThat(orderRepository.countInSlot(company, MONDAY, LocalTime.of(9, 0), -1L)).isEqualTo(2);
        assertThat(orderRepository.countInSlot(company, MONDAY, LocalTime.of(9, 0), pending.getId())).isEqualTo(1);
        assertThat(orderRepository.countInSlot(company, MONDAY, LocalTime.of(9, 30), -1L)).isZero();
    }

    @Test
    @DisplayName("facturación y número de pedidos de un periodo, sin cancelados")
    void revenue() {
        assertThat(orderRepository.sumRevenue(forn.getId(), MONDAY, MONDAY.plusDays(6))).isEqualByComparingTo("20.00");
        assertThat(orderRepository.countBetween(forn.getId(), MONDAY, MONDAY.plusDays(6))).isEqualTo(3);
        assertThat(orderRepository.sumRevenue(forn.getId(), MONDAY.minusDays(7), MONDAY.minusDays(1))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("contadores por estado, por día y por cliente")
    void counters() {
        assertThat(orderRepository.countByCompanyIdAndStatus(forn.getId(), OrderStatus.PENDIENTE)).isEqualTo(1);
        assertThat(orderRepository.countByCompanyIdAndPickupDateAndStatusNot(forn.getId(), MONDAY, OrderStatus.CANCELADO)).isEqualTo(2);
        assertThat(orderRepository.findByCompanyIdAndPickupDateAndStatus(forn.getId(), MONDAY, OrderStatus.PENDIENTE))
                .extracting(Order::getId).containsExactly(pending.getId());
        assertThat(orderRepository.existsByCustomerId(lola.getId())).isTrue();
        assertThat(orderRepository.findByCustomerIdOrderByPickupDateDesc(lola.getId())).hasSize(2)
                .first().extracting(Order::getPickupDate).isEqualTo(MONDAY.plusDays(1));
        assertThat(orderLineRepository.existsByProductId(coca.getId())).isTrue();
    }

    @Test
    @DisplayName("producción: líneas del día sin los estados excluidos")
    void production() {
        List<OrderLine> withoutCancelled = orderLineRepository.findForProduction(forn.getId(), MONDAY, List.of(OrderStatus.CANCELADO));
        List<OrderLine> pendingOnly = orderLineRepository.findForProduction(forn.getId(), MONDAY,
                List.of(OrderStatus.CANCELADO, OrderStatus.ENTREGADO));

        assertThat(withoutCancelled).hasSize(2);
        assertThat(pendingOnly).hasSize(1).first().extracting(l -> l.getProduct().getName()).isEqualTo("Pa");
    }

    @Test
    @DisplayName("unidades por día y productos más vendidos, sin cancelados")
    void statistics() {
        List<Object[]> perDay = orderLineRepository.unitsPerDay(forn.getId(), MONDAY, MONDAY.plusDays(6));
        assertThat(perDay).hasSize(2);
        assertThat(perDay).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(MONDAY);
            assertThat(((Number) row[1]).longValue()).isEqualTo(4);
        });

        List<Object[]> top = orderLineRepository.findTopProducts(forn.getId(), PageRequest.of(0, 5));
        assertThat(top).hasSize(2);
        assertThat(top.getFirst()[0]).isEqualTo("Pa");
        assertThat(((Number) top.getFirst()[1]).longValue()).isEqualTo(5);
    }
}
