package com.pangestion.config;

import com.pangestion.model.*;
import com.pangestion.repository.*;
import com.pangestion.storage.ImageStorage;
import com.pangestion.storage.ImageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga datos de ejemplo la primera vez que arranca la aplicacion (si no hay usuarios).
 * Crea dos empresas independientes (una panaderia y un bar) para comprobar que cada una solo ve lo suyo.
 * Usuarios de prueba (solo desarrollo):
 *   superadmin@admin.es / admin1234  (SUPERADMIN, gestiona las empresas)
 *   Panaderia Demo:    admin@admin.es / admin1234          (ADMIN)
 *                      gestor@pangestion.local / gestor123 (GESTOR)
 *                      visor@pangestion.local / visor123   (VISOR)
 *   El Mosset (bar):   admin2@admin.es / admin1234         (ADMIN)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorage imageStorage;

    @Value("${app.seed-data:true}")
    private boolean seedData;

    /** Empresa a la que se asignan los datos que se van creando. */
    private Company company;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedData || userRepository.count() > 0) {
            return;
        }
        log.info("Base de datos vacia: cargando datos de ejemplo");

        user("Super Admin", "superadmin@admin.es", "admin1234", Role.SUPERADMIN);

        // ===== Empresa 1: Panaderia Demo (catalogo real copiado de la aplicacion de Oberta) =====
        company = company("Panadería Demo", "B00000001", "hola@panaderiademo.es", "960000001", "Calle Mayor 18",
                "07:30", "14:00", 30, 6);
        user("Ana Admin", "admin@admin.es", "admin1234", Role.ADMIN);
        user("Gema Gestora", "gestor@pangestion.local", "gestor123", Role.GESTOR);
        user("Victor Visor", "visor@pangestion.local", "visor123", Role.VISOR);

        // Catalogo real de la panaderia (copiado de la aplicacion de Oberta), en su mismo orden.
        // Sin descripciones todavia. Color nulo = usa el de la categoria.
        Category mones = category("mones", "#DC2626", null);
        Category rollo = category("rollo", "#475569", null);
        Category coques = category("coques", "#8B5CF6", null);
        Category cocodrilos = category("cocodrilos", "#78716C", null);
        Category caiman = category("caimán", "#16A34A", null);
        Category arnadi = category("arnadí", "#CA8A04", null);
        Category pans = category("pans", "#D97706", null);
        Category bolleria = category("bolleria", "#F59E0B", null);
        Category salat = category("salat", "#059669", null);
        Category curasanXl = category("curasan XL", "#DB2777", null);

        // Ingredientes: de que esta hecho cada producto (y sus alergenos)
        Ingredient chocolateNegro = ingredient("Chocolate negro", null);
        Ingredient chocolateBlanco = ingredient("Chocolate blanco", "Lácteos");
        Ingredient nueces = ingredient("Nueces", "Frutos de cáscara");
        Ingredient pasas = ingredient("Pasas", null);
        Ingredient cabello = ingredient("Cabello de ángel", null);
        ingredient("Huevo", "Huevo");
        Ingredient tomate = ingredient("Tomate", null);
        Ingredient espinacas = ingredient("Espinacas", null);
        Ingredient jamonYork = ingredient("Jamón york", null);
        Ingredient queso = ingredient("Queso", "Lácteos");
        Ingredient bacon = ingredient("Bacon", null);
        Ingredient salsaBarbacoa = ingredient("Salsa barbacoa", null);
        Ingredient nata = ingredient("Nata", "Lácteos");
        Ingredient kebab = ingredient("Carne de kebab", null);

        Product monaGran = product("mona gran", "mona", "#DC2626", mones, "4.50", null, true);
        product("mona mitjaneta", "mona", "#DC2626", mones, "2.90", null, true);
        product("mona xicoteta", "mona", "#DC2626", mones, "1.90", null, true);
        product("mona mini", "MINI", "#DC2626", mones, "1.00", null, true);

        Product rolloGran = product("rollo", "Rollo", "#475569", rollo, "4.50", null, true);
        product("rollo mitjanet", "Rollo", "#475569", rollo, "2.80", null, true);
        product("rollo mantel", "Mantel", "#475569", rollo, "4.50", null, true);
        product("rollo xicotet", null, null, rollo, "2.30", null, true);

        product("coca", "C", "#475569", coques, "0.00", null, true);
        Product xocoNueces = product("xoco-nueces", "N-X", "#8B5CF6", coques, "12.90", null, true, chocolateNegro, nueces);
        product("solo xoco", "S-X", "#8B5CF6", coques, "12.00", null, true, chocolateNegro);
        product("mixta", "Mix", "#3B82F6", coques, "15.00", null, true, chocolateBlanco, chocolateNegro, nueces);
        product("mixta sense anous", "M-SN", "#3B82F6", coques, "15.00", null, true, chocolateBlanco, chocolateNegro);
        product("xoco blanc i anous", "N-XB", "#3B82F6", coques, "13.90", null, true, chocolateBlanco, nueces);
        product("anous i panses", "N-P", "#EA580C", coques, "9.90", null, true, nueces, pasas);
        product("anous-panses-cabello", "N-P-C", "#22C55E", coques, "12.90", null, true, nueces, pasas, cabello);
        product("anous i cabello", "N-C", "#22C55E", coques, "12.00", null, true, nueces, cabello);

        product("cocodrilo", "Coco", "#78716C", cocodrilos, "3.50", null, true);

        product("Caimán sin relleno", null, null, caiman, "12.00", null, true);
        product("Caimán Mixto", null, null, caiman, "0.00", null, true);
        Product caimanXoco = product("caimán xoco", null, null, caiman, "14.00", null, true, chocolateNegro);
        product("caimán xoco blanc", null, null, caiman, "14.00", null, true, chocolateBlanco);

        Product arnadiProducte = product("arnadí", null, null, arnadi, "13.00", null, true);

        Product pa1kg = product("pa 1kg", null, null, pans, "3.00", null, true);
        Product paAigua = product("pa aigua", null, null, pans, "1.00", null, true);
        product("pa redo", null, null, pans, "2.20", null, true);
        product("pa coca kg", null, null, pans, "3.00", null, true);
        product("Pa rustic", null, null, pans, "0.00", null, true);

        Product curasanXoco = product("curasan xoco", null, null, bolleria, "0.85", null, true, chocolateNegro);
        Product enseimada = product("enseimada", null, null, bolleria, "0.55", null, true);

        product("empanadilla tomaca", null, null, salat, "1.80", null, true, tomate);
        Product empanadillaEspinacs = product("empanadilla espinacs", null, null, salat, "1.80", null, true, espinacas);
        product("pizza barbacoa", null, null, salat, "2.50", null, true, queso, bacon, salsaBarbacoa);
        product("pizza york", null, null, salat, "2.50", null, true, queso, jamonYork);
        Product pizzaCarbonara = product("pizza carbonara", null, null, salat, "2.50", null, true, queso, bacon, nata);
        product("pizza kebab", null, null, salat, "2.50", null, true, queso, kebab);

        Product curasanGran = product("curasan XL", "CuXL", null, curasanXl, "14.50", null, true);


        // Fotos de los productos (copiadas de la aplicacion de Oberta, en src/main/resources/seed-images)
        image("mona gran", "mona-gran.webp");
        image("mona mitjaneta", "mona-mitjaneta.webp");
        image("mona xicoteta", "mona-xicoteta.webp");
        image("rollo", "rollo.jpg");
        image("rollo mantel", "rollo-mantel.jpg");
        image("coca", "coca.webp");
        image("xoco-nueces", "xoco-nueces.jpg");
        image("solo xoco", "solo-xoco.jpg");
        image("curasan xoco", "curasan-xoco.jpg");

        Customer lola = customer("Lola Martin", "lola@correo.es", "600111222", null, null);
        Customer mario = customer("Mario Ruiz", "mario@correo.es", "600333444", null, null);
        Customer sofia = customer("Sofia Navarro", "sofia@correo.es", "600555666", null, null);
        Customer cafe = customer("Jorge", "jorge@cafeatlas.es", "611000111", "Cafe Atlas S.L.", "B12345678");
        Customer hotel = customer("Marta", "marta@hotelcentral.es", "622000222", "Hotel Central S.A.", "A87654321");

        LocalDate today = LocalDate.now();
        order(lola, today, "09:00", OrderStatus.EN_PROCESO, "Cortar el pan en rebanadas", pa1kg, 2, curasanXoco, 4);
        order(cafe, today, "10:00", OrderStatus.PENDIENTE, "Recoge el repartidor", curasanXoco, 24, enseimada, 24);
        order(mario, today, "11:30", OrderStatus.LISTO, null, xocoNueces, 1);
        order(hotel, today.plusDays(1), "08:00", OrderStatus.PENDIENTE, "Pedido semanal con factura", paAigua, 30, curasanXoco, 40);
        order(sofia, today.plusDays(2), "12:00", OrderStatus.PENDIENTE, "Llamar al llegar", monaGran, 2, rolloGran, 1);
        order(lola, today.minusDays(1), "13:00", OrderStatus.ENTREGADO, null, arnadiProducte, 1, empanadillaEspinacs, 2);
        order(cafe, today.minusDays(2), "09:00", OrderStatus.ENTREGADO, null, pizzaCarbonara, 10, curasanGran, 1);
        order(mario, today.minusDays(3), "10:30", OrderStatus.CANCELADO, "Cancelado por el cliente", caimanXoco, 1);

        // ===== Empresa 2: El Mosset, un bar (carta completa en src/main/resources/seed/el-mosset.json) =====
        loadCompanyFromJson("/seed/el-mosset.json");
        company.setType(BusinessType.BAR);
        user("Pablo Admin", "admin2@admin.es", "admin1234", Role.ADMIN);
        Customer luis = customer("Luis Pérez", "luis@correo.es", "600999888", null, null);
        order(luis, today.plusDays(1), "21:00", OrderStatus.PENDIENTE, "Sin cebolla en la pizza",
                productNamed("Pizzas", "Carbonara"), 2, productNamed("Entrantes", "Patatas fritas o bravas"), 1);
    }

    private void user(String name, String email, String password, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setCompany(role == Role.SUPERADMIN ? null : company);
        userRepository.save(user);
    }

    private Category category(String name, String color, String description) {
        Category category = new Category();
        category.setName(name);
        category.setColor(color);
        category.setDescription(description);
        category.setCompany(company);
        category.setSortOrder(categoryRepository.nextSortOrder(company.getId()));
        return categoryRepository.save(category);
    }

    private Ingredient ingredient(String name, String allergen) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient.setAllergen(allergen);
        ingredient.setCompany(company);
        return ingredientRepository.save(ingredient);
    }

    /** El orden dentro de la categoria es el de creacion. */
    private Product product(String name, String abbreviation, String color, Category category, String price,
                            String description, boolean active, Ingredient... ingredients) {
        Product product = new Product();
        product.setName(name);
        product.setCompany(company);
        product.setAbbreviation(abbreviation);
        product.setColor(color);
        product.setSortOrder(productRepository.nextSortOrder(category.getId()));
        product.setCategory(category);
        product.setPrice(new BigDecimal(price));
        product.setDescription(description);
        product.setActive(active);
        product.getIngredients().addAll(java.util.List.of(ingredients));
        return productRepository.save(product);
    }

    /**
     * Crea una empresa con su catalogo a partir de un JSON: datos de la empresa, ingredientes (con alergenos)
     * y categorias con sus productos. Asi la carta completa de un negocio queda en un fichero facil de revisar.
     */
    @SuppressWarnings("unchecked")
    private void loadCompanyFromJson(String resource) {
        Map<String, Object> data;
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No se encuentra " + resource);
            }
            data = JsonParserFactory.getJsonParser().parseMap(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + resource, e);
        }

        Map<String, Object> c = (Map<String, Object>) data.get("company");
        company = company((String) c.get("name"), (String) c.get("taxId"), (String) c.get("email"),
                (String) c.get("phone"), (String) c.get("address"), (String) c.get("openingTime"),
                (String) c.get("closingTime"), ((Number) c.get("slotMinutes")).intValue(),
                ((Number) c.get("ordersPerSlot")).intValue());
        company.setType(BusinessType.valueOf((String) c.get("type")));

        Map<String, Ingredient> ingredients = new HashMap<>();
        for (Map<String, Object> i : (List<Map<String, Object>>) data.get("ingredients")) {
            String name = (String) i.get("name");
            ingredients.put(name, ingredient(name, (String) i.get("allergen")));
        }
        for (Map<String, Object> cat : (List<Map<String, Object>>) data.get("categories")) {
            Category category = category((String) cat.get("name"), (String) cat.get("color"), (String) cat.get("description"));
            for (Map<String, Object> p : (List<Map<String, Object>>) cat.get("products")) {
                Ingredient[] productIngredients = ((List<String>) p.get("ingredients")).stream()
                        .map(ingredients::get)
                        .toArray(Ingredient[]::new);
                product((String) p.get("name"), null, null, category, (String) p.get("price"),
                        (String) p.get("description"), true, productIngredients);
            }
        }
    }

    /** Producto de la empresa actual por categoria y nombre (para los pedidos de ejemplo). */
    private Product productNamed(String categoryName, String productName) {
        return productRepository.search(company.getId(), productName.toLowerCase(), null, null).stream()
                .filter(p -> p.getName().equals(productName) && p.getCategory().getName().equals(categoryName))
                .findFirst()
                .orElseThrow();
    }

    /** Copia una imagen de seed-images al almacenamiento y se la asigna al producto con ese nombre. */
    private void image(String productName, String file) {
        Product product = productRepository.search(company.getId(), productName.toLowerCase(), null, null).stream()
                .filter(p -> p.getName().equals(productName))
                .findFirst()
                .orElseThrow();
        try (InputStream in = getClass().getResourceAsStream("/seed-images/" + file)) {
            if (in == null) {
                log.warn("No se encuentra la imagen de ejemplo {}", file);
                return;
            }
            byte[] data = in.readAllBytes();
            product.setImagePath(imageStorage.save(company.getId() + "/products", data, ImageType.detect(data)));
        } catch (IOException e) {
            log.warn("No se pudo cargar la imagen de ejemplo {}", file, e);
        }
    }

    private Company company(String name, String taxId, String email, String phone, String address,
                            String open, String close, int slotMinutes, int perSlot) {
        Company company = new Company();
        company.setName(name);
        company.setTaxId(taxId);
        company.setEmail(email);
        company.setPhone(phone);
        company.setAddress(address);
        company.setOpeningTime(LocalTime.parse(open));
        company.setClosingTime(LocalTime.parse(close));
        company.setSlotMinutes(slotMinutes);
        company.setOrdersPerSlot(perSlot);
        return companyRepository.save(company);
    }

    private Customer customer(String name, String email, String phone, String businessName, String taxId) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setBusinessName(businessName);
        customer.setTaxId(taxId);
        return customerRepository.save(customer);
    }

    /** items: pares producto, cantidad. */
    private void order(Customer customer, LocalDate date, String time, OrderStatus status,
                       String notes, Object... items) {
        Order order = new Order();
        order.setCompany(company);
        order.setCustomer(customer);
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
