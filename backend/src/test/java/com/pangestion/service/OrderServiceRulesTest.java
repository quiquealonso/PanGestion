package com.pangestion.service;

import com.pangestion.dto.OrderDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Category;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.model.Ingredient;
import com.pangestion.model.Order;
import com.pangestion.model.OrderLine;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Product;
import com.pangestion.repository.OrderLineRepository;
import com.pangestion.repository.OrderRepository;
import com.pangestion.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S17 · Reglas de negocio de los pedidos, probadas en el servicio con dobles de prueba (Mockito):
 * no hay base de datos ni servidor; los repositorios y los demas servicios son mocks.
 */
@Tag("reglas-negocio") // S17: entra en el CI
@ExtendWith(MockitoExtension.class)
@DisplayName("Unidad · OrderService (reglas de los pedidos)")
class OrderServiceRulesTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    CustomerService customerService;
    @Mock
    CompanyService companyService;
    @Mock
    ProductService productService;
    @Mock
    CurrentUser currentUser;
    @InjectMocks
    OrderService orderService;

    private Company company;
    private Customer lola;
    private Product pizza;
    private Product bravas;
    private Ingredient cebolla;
    private Ingredient queso;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        company.setName("Bar");
        lola = new Customer();
        lola.setId(5L);
        lola.setName("Lola");
        lola.setEmail("lola@correo.es");
        lola.setCompany(company);
        cebolla = ingredient(20L, "Cebolla");
        queso = ingredient(21L, "Queso");
        pizza = product(10L, "Vegetal", "8.00", cebolla, queso);
        bravas = product(11L, "Bravas", "4.50");

        lenient().when(currentUser.companyId()).thenReturn(1L);
        lenient().when(companyService.current()).thenReturn(company);
        lenient().when(customerService.getEntity(5L)).thenReturn(lola);
        lenient().when(productService.getEntity(10L, 1L)).thenReturn(pizza);
        lenient().when(productService.getEntity(11L, 1L)).thenReturn(bravas);
        lenient().when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Ingredient ingredient(Long id, String name) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(id);
        ingredient.setName(name);
        return ingredient;
    }

    private Product product(Long id, String name, String price, Ingredient... ingredients) {
        Category category = new Category();
        category.setColor("#000000");
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setActive(true);
        product.setCompany(company);
        product.setCategory(category);
        product.getIngredients().addAll(List.of(ingredients));
        return product;
    }

    private static OrderDto.Line line(Long productId, int quantity, Long... removed) {
        return new OrderDto.Line(productId, quantity, List.of(removed));
    }

    private static OrderDto.Request request(LocalTime time, OrderDto.Line... lines) {
        return new OrderDto.Request(5L, TOMORROW, time, null, List.of(lines));
    }

    /** Pedido ya guardado que devuelve el repositorio al buscarlo por id. */
    private Order existing(OrderStatus status, Product product) {
        Order order = new Order();
        order.setId(7L);
        order.setCompany(company);
        order.setCustomer(lola);
        order.setPickupDate(TOMORROW);
        order.setPickupTime(TEN);
        order.setStatus(status);
        order.addLine(product, 1);
        when(orderRepository.findByIdAndCompanyId(7L, 1L)).thenReturn(Optional.of(order));
        return order;
    }

    // ----- Lineas -----

    @Test
    @DisplayName("las líneas del mismo producto sin cambios se suman en una")
    void mergesEqualLines() {
        OrderDto.Response order = orderService.create(request(TEN, line(11L, 1), line(11L, 2)));

        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().getFirst().quantity()).isEqualTo(3);
        assertThat(order.total()).isEqualByComparingTo("13.50");
    }

    @Test
    @DisplayName("'sin cebolla' es una línea aparte; quitar los mismos ingredientes en otro orden es la misma línea")
    void customizedLinesAreSeparate() {
        OrderDto.Response order = orderService.create(request(TEN,
                line(10L, 1), line(10L, 1, 20L), line(10L, 1, 21L, 20L), line(10L, 1, 20L, 21L)));

        assertThat(order.lines()).extracting(OrderDto.LineResponse::removedIngredients)
                .containsExactly(List.of(), List.of("Cebolla"), List.of("Cebolla", "Queso"));
        assertThat(order.lines().get(2).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("quitar un ingrediente no cambia el precio")
    void removingDoesNotChangePrice() {
        OrderDto.Response order = orderService.create(request(TEN, line(10L, 2, 20L)));

        assertThat(order.total()).isEqualByComparingTo("16.00");
    }

    @Test
    @DisplayName("no se puede quitar un ingrediente que el producto no lleva")
    void cannotRemoveForeignIngredient() {
        assertThatThrownBy(() -> orderService.create(request(TEN, line(11L, 1, 20L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El producto Bravas no lleva ese ingrediente");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto desactivado no se puede pedir")
    void inactiveProduct() {
        bravas.setActive(false);

        assertThatThrownBy(() -> orderService.create(request(TEN, line(11L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El producto Bravas no esta disponible");
        verify(orderRepository, never()).save(any());
    }

    // ----- Franjas -----

    @Test
    @DisplayName("al crear se valida la franja; si está completa no se guarda nada")
    void createValidatesSlot() {
        org.mockito.Mockito.doThrow(new BusinessException("La franja de las 10:00 esta completa"))
                .when(companyService).validateSlot(company, TOMORROW, TEN, -1L);

        assertThatThrownBy(() -> orderService.create(request(TEN, line(11L, 1))))
                .isInstanceOf(BusinessException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("al editar sin cambiar la franja no se vuelve a validar el aforo")
    void updateSameSlotDoesNotRevalidate() {
        existing(OrderStatus.PENDIENTE, bravas);

        orderService.update(7L, request(TEN, line(11L, 3)));

        verify(companyService, never()).validateSlot(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("al cambiar la franja se valida sin contar el propio pedido")
    void updateNewSlotExcludesItself() {
        existing(OrderStatus.PENDIENTE, bravas);

        orderService.update(7L, request(LocalTime.of(11, 0), line(11L, 1)));

        verify(companyService).validateSlot(company, TOMORROW, LocalTime.of(11, 0), 7L);
    }

    // ----- Estados -----

    @Test
    @DisplayName("un pedido entregado o cancelado no se puede editar")
    void closedOrdersCannotBeEdited() {
        existing(OrderStatus.ENTREGADO, bravas);
        assertThatThrownBy(() -> orderService.update(7L, request(TEN, line(11L, 2))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No se puede editar un pedido entregado o cancelado");
    }

    @Test
    @DisplayName("al editar se mantiene un producto que ya estaba aunque luego se desactivara")
    void updateKeepsDeactivatedProduct() {
        Order order = existing(OrderStatus.PENDIENTE, bravas);
        bravas.setActive(false);

        orderService.update(7L, request(TEN, line(11L, 2)));

        assertThat(order.getLines()).extracting(OrderLine::getQuantity).containsExactly(2);
    }

    @Test
    @DisplayName("'pasar a en proceso' cambia los pendientes del día y dice cuántos")
    void startDay() {
        Order a = new Order();
        Order b = new Order();
        when(orderRepository.findByCompanyIdAndPickupDateAndStatus(1L, TOMORROW, OrderStatus.PENDIENTE)).thenReturn(List.of(a, b));

        assertThat(orderService.startDay(TOMORROW)).isEqualTo(2);
        assertThat(List.of(a, b)).allMatch(o -> o.getStatus() == OrderStatus.EN_PROCESO);
    }

    // ----- Creacion anidada -----

    @Test
    @DisplayName("pedido anidado: si el cliente no es de la empresa, 404 antes de mirar franjas o productos")
    void nestedOrderChecksCustomerFirst() {
        when(customerService.getEntity(99L)).thenThrow(new NotFoundException("Cliente", 99L));

        assertThatThrownBy(() -> orderService.createForCustomer(99L,
                new OrderDto.CustomerOrderRequest(TOMORROW, TEN, null, List.of(line(11L, 1)))))
                .isInstanceOf(NotFoundException.class);
        verify(companyService, never()).validateSlot(any(), any(), any(), eq(-1L));
        verify(orderRepository, never()).save(any());
    }
}
