package com.pangestion.support;

import com.pangestion.model.BusinessType;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Ingredient;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Base de los tests de repositorio: solo arranca JPA contra una H2 en memoria (sin web ni seguridad).
 * Cada test se deshace al terminar. Los datos se guardan con TestEntityManager.
 */
@DataJpaTest
public abstract class RepositoryTestSupport {

    @Autowired
    protected TestEntityManager em;

    private int sequence;

    protected Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setType(BusinessType.PANADERIA);
        company.setTaxId("B" + (20_000_000 + ++sequence));
        company.setAddress("Calle 1");
        company.setOpeningTime(LocalTime.of(8, 0));
        company.setClosingTime(LocalTime.of(20, 0));
        return em.persist(company);
    }

    protected Category category(Company company, String name, int sortOrder) {
        Category category = new Category();
        category.setCompany(company);
        category.setName(name);
        category.setColor("#123456");
        category.setSortOrder(sortOrder);
        return em.persist(category);
    }

    protected Ingredient ingredient(Company company, String name) {
        Ingredient ingredient = new Ingredient();
        ingredient.setCompany(company);
        ingredient.setName(name);
        return em.persist(ingredient);
    }

    protected Product product(Category category, String name, String price, int sortOrder, boolean active, Ingredient... ingredients) {
        Product product = new Product();
        product.setCompany(category.getCompany());
        product.setCategory(category);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setSortOrder(sortOrder);
        product.setActive(active);
        product.getIngredients().addAll(List.of(ingredients));
        return em.persist(product);
    }

    protected Customer customer(Company company, String name, String email, String businessName) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setName(name);
        customer.setEmail(email);
        customer.setBusinessName(businessName);
        return em.persist(customer);
    }

    /** items: producto, cantidad, producto, cantidad... */
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
        return em.persist(order);
    }

    /** Escribe en la base de datos y vacia la cache, para que las consultas lean de verdad de la BD. */
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
