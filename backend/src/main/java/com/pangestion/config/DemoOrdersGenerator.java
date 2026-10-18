package com.pangestion.config;

import com.pangestion.model.*;
import com.pangestion.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Genera pedidos de demostracion para una empresa en un rango de fechas (de lunes a sabado).
 * Solo se ejecuta si se arranca con, por ejemplo:
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.demo-orders.company-id=1 --app.demo-orders.from=2026-10-12 --app.demo-orders.to=2026-10-24"
 * </pre>
 * - Dias pasados: pedidos entregados (y algun cancelado). Hoy y dias futuros: pendientes.
 * - Usa una semilla fija, asi que siempre genera los mismos datos. Se salta los dias que ya tienen pedidos.
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(2) // despues de DataInitializer (el nombre completo evita el choque con la entidad Order)
@RequiredArgsConstructor
public class DemoOrdersGenerator implements CommandLineRunner {

    private static final String[] FIRST_NAMES = {"Carmen", "José", "Pilar", "Vicent", "Amparo", "Toni", "Rosa", "Pepe",
            "Lucía", "Miguel", "Elena", "Juanjo", "Marta", "Salva", "Nuria", "Paco", "Inma", "Raúl", "Teresa", "Xavi"};
    private static final String[] LAST_NAMES = {"García", "Martínez", "Ferrer", "Sanchis", "Navarro", "Llopis", "Soler",
            "Climent", "Ribes", "Torres", "Gil", "Peris"};
    private static final String[] NOTES = {"Cortar el pan, por favor", "Recoge su hija", "Llamar si hay retraso",
            "Bien hecho", "Poco hecho", "Para un cumpleaños", "Pagado por adelantado"};
    /** Categorias que se piden mas en una panaderia. */
    private static final Set<String> POPULAR = Set.of("pans", "bolleria", "mones");

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Value("${app.demo-orders.company-id:}")
    private String companyIdParam;

    /** Fechas en formato ISO: 2026-10-12. */
    @Value("${app.demo-orders.from:}")
    private String fromParam;

    @Value("${app.demo-orders.to:}")
    private String toParam;

    private final Random random = new Random(42);

    @Override
    @Transactional
    public void run(String... args) {
        if (companyIdParam.isBlank() || fromParam.isBlank() || toParam.isBlank()) {
            return;
        }
        Long companyId = Long.valueOf(companyIdParam);
        LocalDate from = LocalDate.parse(fromParam);
        LocalDate to = LocalDate.parse(toParam);
        Company company = companyRepository.findById(companyId).orElseThrow();
        List<Product> products = productRepository.search(companyId, "%", null, true).stream()
                .filter(p -> p.getPrice().signum() > 0)
                .toList();
        List<Product> popular = products.stream()
                .filter(p -> POPULAR.contains(p.getCategory().getName().toLowerCase()))
                .toList();
        List<Customer> customers = demoCustomers(company);
        List<LocalTime> slots = slotTimes(company);
        LocalDate today = LocalDate.now();

        int created = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            if (orderRepository.countByCompanyIdAndPickupDateAndStatusNot(companyId, day, OrderStatus.CANCELADO) > 0) {
                log.info("Pedidos de demo: {} ya tiene pedidos, se salta", day);
                continue;
            }
            boolean saturday = day.getDayOfWeek() == DayOfWeek.SATURDAY;
            int count = saturday ? 32 + random.nextInt(9) : 18 + random.nextInt(9);
            Map<LocalTime, Integer> perSlot = new HashMap<>();
            for (int i = 0; i < count; i++) {
                LocalTime time = pickSlot(slots, perSlot, company.getOrdersPerSlot());
                if (time == null) {
                    break; // el dia esta completo
                }
                Customer customer = customers.get(random.nextInt(customers.size()));
                Order order = new Order();
                order.setCompany(company);
                order.setCustomer(customer);
                order.setPickupDate(day);
                order.setPickupTime(time);
                order.setStatus(statusFor(day, today));
                if (random.nextInt(6) == 0) {
                    order.setNotes(NOTES[random.nextInt(NOTES.length)]);
                }
                addLines(order, products, popular, customer.getBusinessName() != null);
                orderRepository.save(order);
                created++;
            }
        }
        log.info("Pedidos de demo: creados {} pedidos para {} entre {} y {}", created, company.getName(), from, to);
    }

    /** Los clientes que ya tiene la empresa mas unos cuantos particulares de ejemplo. */
    private List<Customer> demoCustomers(Company company) {
        List<Customer> customers = new ArrayList<>(customerRepository.search(company.getId(), "%"));
        for (int i = 1; i <= 18; i++) {
            String email = "cliente" + i + "@correo.es";
            if (customerRepository.existsByCompanyIdAndEmailIgnoreCase(company.getId(), email)) {
                continue;
            }
            Customer customer = new Customer();
            customer.setCompany(company);
            customer.setName(FIRST_NAMES[(i * 7) % FIRST_NAMES.length] + " " + LAST_NAMES[(i * 5) % LAST_NAMES.length]);
            customer.setEmail(email);
            customer.setPhone("6" + (10000000 + random.nextInt(89999999)));
            customers.add(customerRepository.save(customer));
        }
        return customers;
    }

    /** Las primeras horas tienen mas pedidos. Devuelve null si no queda hueco. */
    private LocalTime pickSlot(List<LocalTime> slots, Map<LocalTime, Integer> perSlot, int capacity) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int index = random.nextInt(10) < 6
                    ? random.nextInt(Math.max(1, slots.size() / 2))
                    : random.nextInt(slots.size());
            LocalTime time = slots.get(index);
            if (perSlot.getOrDefault(time, 0) < capacity) {
                perSlot.merge(time, 1, Integer::sum);
                return time;
            }
        }
        return null;
    }

    private OrderStatus statusFor(LocalDate day, LocalDate today) {
        if (day.isBefore(today)) {
            return random.nextInt(20) == 0 ? OrderStatus.CANCELADO : OrderStatus.ENTREGADO;
        }
        return OrderStatus.PENDIENTE;
    }

    /** 1 a 4 productos distintos; los populares salen mas. Los negocios piden cantidades grandes. */
    private void addLines(Order order, List<Product> products, List<Product> popular, boolean business) {
        int lines = 1 + random.nextInt(business ? 3 : 4);
        Set<Long> used = new HashSet<>();
        for (int i = 0; i < lines; i++) {
            Product product = !popular.isEmpty() && random.nextInt(10) < 6
                    ? popular.get(random.nextInt(popular.size()))
                    : products.get(random.nextInt(products.size()));
            if (!used.add(product.getId())) {
                continue;
            }
            int quantity = business ? 6 + random.nextInt(25) : 1 + random.nextInt(random.nextInt(4) == 0 ? 6 : 3);
            List<String> removed = List.of();
            if (!product.getIngredients().isEmpty() && random.nextInt(8) == 0) {
                List<Ingredient> ingredients = new ArrayList<>(product.getIngredients());
                removed = List.of(ingredients.get(random.nextInt(ingredients.size())).getName());
            }
            order.addLine(product, quantity, removed);
        }
    }

    private List<LocalTime> slotTimes(Company company) {
        List<LocalTime> times = new ArrayList<>();
        int close = company.getClosingTime().toSecondOfDay() / 60;
        for (int m = company.getOpeningTime().toSecondOfDay() / 60; m < close; m += company.getSlotMinutes()) {
            times.add(LocalTime.of(m / 60, m % 60));
        }
        return times;
    }
}
