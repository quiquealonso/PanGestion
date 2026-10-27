package com.pangestion.config;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.repository.CompanyRepository;
import com.pangestion.repository.OrderRepository;
import com.pangestion.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Datos de ejemplo al arrancar con la base de datos vacia (DataInitializer) y pedidos de demostracion
 * (DemoOrdersGenerator) para la semana del 7 al 13 de enero de 2030.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pangestion-seed-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.seed-data=true",
        "app.demo-orders.company-id=1",
        "app.demo-orders.from=2030-01-07",
        "app.demo-orders.to=2030-01-13"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Arranque · datos de ejemplo y pedidos de demostración")
class SeedDataTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Autowired
    MockMvc mvc;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    DemoOrdersGenerator demoOrdersGenerator;

    @Test
    @DisplayName("crea la panadería y el bar El Mosset con su tipo de negocio y su carta")
    void companies() {
        List<Company> companies = companyRepository.findAllByOrderByNameAsc();

        assertThat(companies).extracting(Company::getName).containsExactly("El Mosset", "Panadería Demo");
        assertThat(companies.get(0).getType()).isEqualTo(BusinessType.BAR);
        assertThat(companies.get(1).getType()).isEqualTo(BusinessType.PANADERIA);
        assertThat(productRepository.search(companies.get(0).getId(), "%", null, null)).hasSize(85);
    }

    @Test
    @DisplayName("los usuarios de prueba pueden entrar con sus contraseñas")
    void testUsersCanLogIn() throws Exception {
        Map<String, String> users = Map.of(
                "superadmin@admin.es", "admin1234",
                "admin@admin.es", "admin1234",
                "admin2@admin.es", "admin1234",
                "gestor@pangestion.local", "gestor123",
                "visor@pangestion.local", "visor123");
        for (var user : users.entrySet()) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getKey(), user.getValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.email").value(user.getKey()));
        }
    }

    @Test
    @DisplayName("pedidos de demo: de lunes a sábado, nunca en domingo, sin pasar el aforo de cada franja")
    void demoOrders() {
        Company company = companyRepository.findById(1L).orElseThrow();
        List<Order> orders = orderRepository.search(1L, "%", null, MONDAY, MONDAY.plusDays(6));

        assertThat(orders).isNotEmpty();
        assertThat(orders).noneMatch(o -> o.getPickupDate().getDayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(orders.stream().map(Order::getPickupDate).distinct()).hasSize(6);
        assertThat(orders).allMatch(o -> o.getStatus() == OrderStatus.PENDIENTE); // fechas futuras
        assertThat(orders).allMatch(o -> !o.getLines().isEmpty());

        Map<String, Long> perSlot = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getPickupDate() + " " + o.getPickupTime(), Collectors.counting()));
        assertThat(perSlot.values()).allMatch(count -> count <= company.getOrdersPerSlot());
    }

    @Test
    @DisplayName("pedidos de demo: volver a lanzarlo no duplica los días que ya tienen pedidos")
    void demoOrdersAreNotDuplicated() {
        long before = orderRepository.countByCompanyId(1L);

        demoOrdersGenerator.run();

        assertThat(orderRepository.countByCompanyId(1L)).isEqualTo(before);
    }
}
