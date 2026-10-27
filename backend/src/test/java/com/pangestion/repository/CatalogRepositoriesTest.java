package com.pangestion.repository;

import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Ingredient;
import com.pangestion.model.Product;
import com.pangestion.support.RepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.hibernate.exception.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Consultas del catalogo: productos, categorias, ingredientes y clientes. */
@DisplayName("Repositorio · catálogo y clientes")
class CatalogRepositoriesTest extends RepositoryTestSupport {

    @Autowired
    ProductRepository productRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    CustomerRepository customerRepository;

    private Company forn;
    private Company bar;
    private Category pans;
    private Category dolcos;
    private Ingredient nous;

    @BeforeEach
    void setUp() {
        forn = company("Forn");
        bar = company("Bar");
        dolcos = category(forn, "Dolços", 1);
        pans = category(forn, "Pans", 0);
        nous = ingredient(forn, "Nous");
        product(pans, "Pa de llenya", "2.10", 1, true);
        product(pans, "Barra", "1.10", 0, true);
        product(dolcos, "Coca de nous", "6.00", 0, false, nous);
        product(category(bar, "Pizzas", 0), "Pizza barra", "8.00", 0, true);
        customer(forn, "Lola Ferrer", "lola@correo.es", null);
        customer(forn, "Hotel Central", "compras@hotel.es", "Hotel Central SL");
        customer(bar, "Lola del bar", "lola@bar.es", null);
        flushAndClear();
    }

    @Test
    @DisplayName("productos: solo los de la empresa, ordenados por categoría y posición")
    void productSearchOrder() {
        assertThat(productRepository.search(forn.getId(), "%", null, null))
                .extracting(Product::getName)
                .containsExactly("Barra", "Pa de llenya", "Coca de nous");
    }

    @Test
    @DisplayName("productos: filtro por texto (sin distinguir mayúsculas), categoría y activos")
    void productSearchFilters() {
        assertThat(productRepository.search(forn.getId(), "%barra%", null, null)).extracting(Product::getName)
                .containsExactly("Barra");
        assertThat(productRepository.search(forn.getId(), "%", dolcos.getId(), null)).extracting(Product::getName)
                .containsExactly("Coca de nous");
        assertThat(productRepository.search(forn.getId(), "%", null, true)).hasSize(2);
        assertThat(productRepository.search(forn.getId(), "%", null, false)).extracting(Product::getName)
                .containsExactly("Coca de nous");
    }

    @Test
    @DisplayName("siguiente posición libre: 0 en una categoría vacía y max+1 en las demás")
    void nextSortOrder() {
        assertThat(productRepository.nextSortOrder(pans.getId())).isEqualTo(2);
        assertThat(productRepository.nextSortOrder(category(forn, "Vacía", 5).getId())).isZero();
        assertThat(categoryRepository.nextSortOrder(forn.getId())).isEqualTo(6);
        assertThat(categoryRepository.nextSortOrder(company("Nueva").getId())).isZero();
    }

    @Test
    @DisplayName("contadores: productos activos, por categoría y por ingrediente")
    void counters() {
        assertThat(productRepository.countByCompanyIdAndActiveTrue(forn.getId())).isEqualTo(2);
        assertThat(productRepository.countByCategoryId(pans.getId())).isEqualTo(2);
        assertThat(productRepository.countByIngredientsId(nous.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("categorías e ingredientes: listados y nombres repetidos por empresa")
    void categoriesAndIngredients() {
        assertThat(categoryRepository.findAllByCompanyIdOrderBySortOrderAscNameAsc(forn.getId()))
                .extracting(Category::getName).containsExactly("Pans", "Dolços");
        assertThat(categoryRepository.existsByCompanyIdAndNameIgnoreCase(forn.getId(), "PANS")).isTrue();
        assertThat(categoryRepository.existsByCompanyIdAndNameIgnoreCase(bar.getId(), "Pans")).isFalse();
        assertThat(categoryRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(forn.getId(), "Pans", pans.getId())).isFalse();
        assertThat(ingredientRepository.existsByCompanyIdAndNameIgnoreCase(forn.getId(), "nous")).isTrue();
        assertThat(ingredientRepository.findByIdAndCompanyId(nous.getId(), bar.getId())).isEmpty();
    }

    @Test
    @DisplayName("la base de datos impide nombres de categoría repetidos en la misma empresa")
    void uniqueConstraints() {
        // Con ids IDENTITY el INSERT (y el error) llega al guardar, no al hacer flush
        assertThatThrownBy(() -> category(forn, "Pans", 9)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("la base de datos impide clientes con el mismo email en la misma empresa")
    void uniqueCustomerEmail() {
        assertThatThrownBy(() -> customer(forn, "Otra Lola", "lola@correo.es", null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el mismo nombre o email en otra empresa sí se permite")
    void sameNamesInOtherCompany() {
        category(bar, "Pans", 1);
        customer(bar, "Lola", "lola@correo.es", null);
        flushAndClear();
        assertThat(categoryRepository.existsByCompanyIdAndNameIgnoreCase(bar.getId(), "Pans")).isTrue();
    }

    @Test
    @DisplayName("clientes: busca por nombre, email o razón social dentro de la empresa")
    void customerSearch() {
        assertThat(customerRepository.search(forn.getId(), "%lola%")).hasSize(1);
        assertThat(customerRepository.search(forn.getId(), "%hotel.es%")).hasSize(1);
        assertThat(customerRepository.search(forn.getId(), "%central sl%")).hasSize(1);
        assertThat(customerRepository.search(forn.getId(), "%")).hasSize(2);
        assertThat(customerRepository.findByCompanyIdAndEmailIgnoreCase(forn.getId(), "LOLA@CORREO.ES")).isPresent();
        assertThat(customerRepository.countByCompanyId(bar.getId())).isEqualTo(1);
    }
}
