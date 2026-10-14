package com.pangestion.config;

import com.pangestion.model.*;
import com.pangestion.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Carga datos de ejemplo la primera vez que arranca la aplicacion (si no hay usuarios).
 * Usuarios de prueba (solo desarrollo):
 *   admin@pangestion.local / admin123   (ADMIN)
 *   gestor@pangestion.local / gestor123 (GESTOR)
 *   visor@pangestion.local / visor123   (VISOR)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-data:true}")
    private boolean seedData;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedData || userRepository.count() > 0) {
            return;
        }
        log.info("Base de datos vacia: cargando datos de ejemplo");

        user("Ana Admin", "admin@pangestion.local", "admin123", Role.ADMIN);
        user("Gema Gestora", "gestor@pangestion.local", "gestor123", Role.GESTOR);
        user("Victor Visor", "visor@pangestion.local", "visor123", Role.VISOR);

        Category panes = category("Panes", "#D97706", "Panes de elaboracion diaria");
        Category bolleria = category("Bolleria", "#F59E0B", "Croissants, napolitanas y bolleria fina");
        Category dulces = category("Dulces", "#DB2777", "Tartas, galletas y reposteria");
        Category salados = category("Salados", "#059669", "Empanadas y cocas saladas");

        Ingredient harina = ingredient("Harina de trigo", "kg", "48", "10", "0.85", "Gluten");
        Ingredient centenoHarina = ingredient("Harina de centeno", "kg", "6", "8", "1.40", "Gluten");
        Ingredient azucar = ingredient("Azucar", "kg", "12", "5", "1.10", null);
        Ingredient chocolate = ingredient("Chocolate negro", "kg", "7", "3", "9.50", "Lacteos");
        Ingredient mantequilla = ingredient("Mantequilla", "kg", "2", "4", "8.20", "Lacteos");
        Ingredient huevos = ingredient("Huevos", "ud", "180", "60", "0.22", "Huevo");
        Ingredient levadura = ingredient("Levadura", "kg", "3", "1", "4.00", null);
        Ingredient atun = ingredient("Atun", "kg", "4", "2", "12.00", "Pescado");

        Product masaMadre = product("Pan de masa madre", panes, "3.20", "Fermentacion larga y corteza crujiente", true,
                harina, "0.5", levadura, "0.01");
        Product centeno = product("Pan de centeno", panes, "3.80", "Pan denso con un 70% de centeno", true,
                centenoHarina, "0.35", harina, "0.15", levadura, "0.01");
        Product baguette = product("Baguette", panes, "1.20", "Baguette tradicional", true,
                harina, "0.25", levadura, "0.005");
        Product croissant = product("Croissant", bolleria, "1.60", "Hojaldre con mantequilla", true,
                harina, "0.06", mantequilla, "0.03", huevos, "0.2");
        Product napolitana = product("Napolitana de chocolate", bolleria, "1.80", "Hojaldre relleno de chocolate", true,
                harina, "0.06", mantequilla, "0.025", chocolate, "0.02");
        Product tarta = product("Tarta de chocolate", dulces, "18.50", "Bizcocho humedo con cobertura de chocolate", true,
                harina, "0.3", chocolate, "0.25", azucar, "0.2", huevos, "4");
        Product galletas = product("Galletas de mantequilla (12 ud)", dulces, "4.50", "Caja de doce galletas", true,
                harina, "0.2", mantequilla, "0.1", azucar, "0.08");
        Product empanada = product("Empanada de atun", salados, "12.00", "Empanada gallega familiar", true,
                harina, "0.4", atun, "0.25", huevos, "2");
        product("Roscon de Reyes", dulces, "22.00", "Disponible solo en temporada", false,
                harina, "0.5", azucar, "0.15");

        Store centro = store("Panaderia Centro", "Calle Mayor 18", "960000001", "07:30", "14:00", 30, 6);
        Store barrio = store("Panaderia El Barrio", "Av. del Puerto 55", "960000002", "08:00", "20:00", 60, 8);

        Company atlas = company("Cafe Atlas S.L.", "B12345678", "pedidos@cafeatlas.es", "961111111", "Av. del Puerto 120");
        Company hotel = company("Hotel Central S.A.", "A87654321", "compras@hotelcentral.es", "962222222", "Gran Via 77");

        Customer lola = customer("Lola Martin", "lola@correo.es", "600111222", null);
        Customer mario = customer("Mario Ruiz", "mario@correo.es", "600333444", null);
        Customer sofia = customer("Sofia Navarro", "sofia@correo.es", "600555666", null);
        Customer cafe = customer("Jorge (Cafe Atlas)", "jorge@cafeatlas.es", "611000111", atlas);
        Customer hotelCli = customer("Marta (Hotel Central)", "marta@hotelcentral.es", "622000222", hotel);

        LocalDate today = LocalDate.now();
        order(lola, centro, today, "09:00", OrderStatus.EN_PROCESO, "Cortar el pan en rebanadas", masaMadre, 2, croissant, 4);
        order(cafe, barrio, today, "10:00", OrderStatus.PENDIENTE, "Recoge el repartidor", croissant, 24, napolitana, 24);
        order(mario, centro, today, "11:30", OrderStatus.LISTO, null, tarta, 1);
        order(hotelCli, barrio, today.plusDays(1), "08:00", OrderStatus.PENDIENTE, "Pedido semanal con factura", baguette, 30, croissant, 40);
        order(sofia, centro, today.plusDays(2), "12:00", OrderStatus.PENDIENTE, "Llamar al llegar", empanada, 1, galletas, 2);
        order(lola, barrio, today.minusDays(1), "18:00", OrderStatus.ENTREGADO, null, centeno, 1, baguette, 2);
        order(cafe, barrio, today.minusDays(2), "09:00", OrderStatus.ENTREGADO, null, croissant, 20, napolitana, 20);
        order(mario, centro, today.minusDays(3), "10:30", OrderStatus.CANCELADO, "Cancelado por el cliente", tarta, 1);
    }

    private void user(String name, String email, String password, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);
    }

    private Category category(String name, String color, String description) {
        Category category = new Category();
        category.setName(name);
        category.setColor(color);
        category.setDescription(description);
        return categoryRepository.save(category);
    }

    private Ingredient ingredient(String name, String unit, String stock, String minStock, String unitCost, String allergen) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient.setUnit(unit);
        ingredient.setStock(new BigDecimal(stock));
        ingredient.setMinStock(new BigDecimal(minStock));
        ingredient.setUnitCost(new BigDecimal(unitCost));
        ingredient.setAllergen(allergen);
        return ingredientRepository.save(ingredient);
    }

    /** recipe: pares ingrediente, cantidad. */
    private Product product(String name, Category category, String price, String description, boolean active,
                            Object... recipe) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setPrice(new BigDecimal(price));
        product.setDescription(description);
        product.setActive(active);
        for (int i = 0; i < recipe.length; i += 2) {
            product.addIngredient((Ingredient) recipe[i], new BigDecimal((String) recipe[i + 1]));
        }
        return productRepository.save(product);
    }

    private Store store(String name, String address, String phone, String open, String close, int slotMinutes, int perSlot) {
        Store store = new Store();
        store.setName(name);
        store.setAddress(address);
        store.setPhone(phone);
        store.setOpeningTime(LocalTime.parse(open));
        store.setClosingTime(LocalTime.parse(close));
        store.setSlotMinutes(slotMinutes);
        store.setOrdersPerSlot(perSlot);
        return storeRepository.save(store);
    }

    private Company company(String name, String taxId, String email, String phone, String address) {
        Company company = new Company();
        company.setName(name);
        company.setTaxId(taxId);
        company.setEmail(email);
        company.setPhone(phone);
        company.setAddress(address);
        return companyRepository.save(company);
    }

    private Customer customer(String name, String email, String phone, Company company) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setCompany(company);
        return customerRepository.save(customer);
    }

    /** items: pares producto, cantidad. */
    private void order(Customer customer, Store store, LocalDate date, String time, OrderStatus status,
                       String notes, Object... items) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setStore(store);
        order.setPickupDate(date);
        order.setPickupTime(LocalTime.parse(time));
        order.setStatus(status);
        order.setNotes(notes);
        for (int i = 0; i < items.length; i += 2) {
            order.addLine((Product) items[i], (Integer) items[i + 1]);
        }
        orderRepository.save(order);
    }
}
