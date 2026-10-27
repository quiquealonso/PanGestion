package com.pangestion.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de las propias entidades: total del pedido, lineas y color del producto. */
@Tag("reglas-negocio") // S17: entra en el CI
@DisplayName("Unidad · Order / OrderLine / Product")
class OrderModelTest {

    private static Product product(String name, String price, String color, String categoryColor) {
        Category category = new Category();
        category.setColor(categoryColor);
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setColor(color);
        product.setCategory(category);
        return product;
    }

    @Test
    @DisplayName("el total es la suma de precio × cantidad de cada línea")
    void totalAndItemCount() {
        Order order = new Order();
        order.addLine(product("Pan", "1.20", null, "#000000"), 3);
        order.addLine(product("Coca", "4.50", null, "#000000"), 2);

        assertThat(order.getTotal()).isEqualByComparingTo("12.60");
        assertThat(order.getItemCount()).isEqualTo(5);
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getLines().getFirst().getSubtotal()).isEqualByComparingTo("3.60");
    }

    @Test
    @DisplayName("la línea copia el precio del producto: si luego cambia, el pedido no cambia")
    void linePriceIsFrozen() {
        Product pan = product("Pan", "1.20", null, "#000000");
        Order order = new Order();
        order.addLine(pan, 1);

        pan.setPrice(new BigDecimal("9.99"));

        assertThat(order.getLines().getFirst().getUnitPrice()).isEqualByComparingTo("1.20");
        assertThat(order.getTotal()).isEqualByComparingTo("1.20");
    }

    @Test
    @DisplayName("una línea personalizada guarda los ingredientes quitados y cuesta lo mismo")
    void customizedLine() {
        Order order = new Order();
        order.addLine(product("Pizza", "8.00", null, "#000000"), 2, List.of("Cebolla"));

        assertThat(order.getLines().getFirst().getRemovedIngredients()).containsExactly("Cebolla");
        assertThat(order.getTotal()).isEqualByComparingTo("16.00");
    }

    @Test
    @DisplayName("vaciar las líneas deja el total a cero")
    void clearLines() {
        Order order = new Order();
        order.addLine(product("Pan", "1.20", null, "#000000"), 3);

        order.clearLines();

        assertThat(order.getLines()).isEmpty();
        assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getItemCount()).isZero();
    }

    @Test
    @DisplayName("un pedido nuevo está PENDIENTE")
    void newOrderIsPending() {
        assertThat(new Order().getStatus()).isEqualTo(OrderStatus.PENDIENTE);
    }

    @Test
    @DisplayName("el color que se pinta es el del producto o, si no tiene, el de su categoría")
    void displayColor() {
        assertThat(product("A", "1", "#FF0000", "#00FF00").getDisplayColor()).isEqualTo("#FF0000");
        assertThat(product("B", "1", null, "#00FF00").getDisplayColor()).isEqualTo("#00FF00");
    }
}
