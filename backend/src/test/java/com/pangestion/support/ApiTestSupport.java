package com.pangestion.support;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.CustomerAccount;
import com.pangestion.model.Ingredient;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.repository.CategoryRepository;
import com.pangestion.repository.CompanyRepository;
import com.pangestion.repository.CustomerAccountRepository;
import com.pangestion.repository.CustomerRepository;
import com.pangestion.repository.IngredientRepository;
import com.pangestion.repository.OrderRepository;
import com.pangestion.repository.ProductRepository;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de los tests HTTP: arranca la aplicacion entera (seguridad incluida) contra H2 en memoria y
 * prueba la API con MockMvc, como lo haria un cliente HTTP.
 * <p>
 * Cada test corre en una transaccion que se deshace al terminar, asi que los tests no se pisan los datos.
 * Los datos se crean con los metodos de ayuda (empresa, usuario, producto...) directamente en los repositorios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestSupport {

    /** Contrasena de todos los usuarios creados con {@link #user}. */
    protected static final String PASSWORD = "secreto123";
    /** Manana: siempre es una fecha valida para recoger un pedido. */
    protected static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected CompanyRepository companyRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected CategoryRepository categoryRepository;
    @Autowired
    protected IngredientRepository ingredientRepository;
    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected CustomerRepository customerRepository;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected CustomerAccountRepository customerAccountRepository;
    @Autowired
    protected TokenService tokenService;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    // ----- Datos de prueba -----

    /** Empresa abierta de 08:00 a 20:00, franjas de 30 minutos y 2 pedidos por franja. */
    protected Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setType(BusinessType.PANADERIA);
        company.setTaxId("B" + (10_000_000 + SEQUENCE.incrementAndGet()));
        company.setAddress("Calle de " + name + " 1");
        company.setPhone("960000000");
        company.setOpeningTime(LocalTime.of(8, 0));
        company.setClosingTime(LocalTime.of(20, 0));
        company.setSlotMinutes(30);
        company.setOrdersPerSlot(2);
        company.setActive(true);
        return companyRepository.save(company);
    }

    /** Usuario activo con la contrasena {@link #PASSWORD}. Un SUPERADMIN no tiene empresa. */
    protected User user(Company company, Role role) {
        User user = new User();
        user.setName(role.name() + " " + SEQUENCE.incrementAndGet());
        user.setEmail(role.name().toLowerCase() + SEQUENCE.incrementAndGet() + "@test.es");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        user.setCompany(role == Role.SUPERADMIN ? null : company);
        return userRepository.save(user);
    }

    protected Category category(Company company, String name) {
        Category category = new Category();
        category.setCompany(company);
        category.setName(name);
        category.setColor("#F59E0B");
        category.setSortOrder(categoryRepository.nextSortOrder(company.getId()));
        return categoryRepository.save(category);
    }

    protected Ingredient ingredient(Company company, String name, String allergen) {
        Ingredient ingredient = new Ingredient();
        ingredient.setCompany(company);
        ingredient.setName(name);
        ingredient.setAllergen(allergen);
        return ingredientRepository.save(ingredient);
    }

    protected Product product(Category category, String name, String price, Ingredient... ingredients) {
        Product product = new Product();
        product.setCompany(category.getCompany());
        product.setCategory(category);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setActive(true);
        product.setSortOrder(productRepository.nextSortOrder(category.getId()));
        product.getIngredients().addAll(List.of(ingredients));
        return productRepository.save(product);
    }

    protected Customer customer(Company company, String name, String email) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setName(name);
        customer.setEmail(email);
        return customerRepository.save(customer);
    }

    /**
     * Pedido guardado directamente (sin pasar por las reglas de franjas), util para preparar escenarios
     * en fechas pasadas o de hoy. items: producto, cantidad, producto, cantidad...
     */
    protected Order order(Customer customer, LocalDate date, String time, OrderStatus status, Object... items) {
        Order order = new Order();
        order.setCompany(customer.getCompany());
        order.setCustomer(customer);
        order.setPickupDate(date);
        order.setPickupTime(LocalTime.parse(time));
        order.setStatus(status);
        for (int i = 0; i < items.length; i += 2) {
            order.addLine((Product) items[i], (Integer) items[i + 1]);
        }
        return orderRepository.save(order);
    }

    /** Cuenta de cliente de la web publica, con el email confirmado y la contrasena {@link #PASSWORD}. */
    protected CustomerAccount account(String name, String email, String phone) {
        CustomerAccount account = new CustomerAccount();
        account.setName(name);
        account.setEmail(email);
        account.setPhone(phone);
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setVerified(true);
        return customerAccountRepository.save(account);
    }

    // ----- Peticiones -----

    /** Anade la cabecera Authorization con un token valido del usuario. */
    protected RequestPostProcessor as(User user) {
        String header = "Bearer " + tokenService.generateToken(user);
        return request -> {
            request.addHeader("Authorization", header);
            return request;
        };
    }

    /** Token de un cliente de la web publica (rol CLIENTE). */
    protected RequestPostProcessor asCustomer(CustomerAccount account) {
        String header = "Bearer " + tokenService.generateCustomerToken(account);
        return request -> {
            request.addHeader("Authorization", header);
            return request;
        };
    }

    protected MockHttpServletRequestBuilder getJson(String url, Object... vars) {
        return get(url, vars).accept(MediaType.APPLICATION_JSON);
    }

    protected MockHttpServletRequestBuilder postJson(String url, String body, Object... vars) {
        return post(url, vars).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder putJson(String url, String body, Object... vars) {
        return put(url, vars).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder patchJson(String url, String body, Object... vars) {
        return patch(url, vars).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder deleteRequest(String url, Object... vars) {
        return delete(url, vars);
    }
}
