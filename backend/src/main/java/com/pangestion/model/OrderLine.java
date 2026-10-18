package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Linea de pedido. Guarda el precio unitario del momento de la compra y los ingredientes quitados. */
@Entity
@Table(name = "order_lines")
@Getter
@Setter
@NoArgsConstructor
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Ingredientes que el cliente ha pedido quitar ("pizza sin cebolla").
     * Se guarda el nombre, igual que el precio: el pedido no cambia aunque luego se renombre o borre el ingrediente.
     */
    @ElementCollection
    @CollectionTable(name = "order_line_removed_ingredients", joinColumns = @JoinColumn(name = "order_line_id"))
    @Column(name = "ingredient_name", nullable = false, length = 100)
    private List<String> removedIngredients = new ArrayList<>();

    public OrderLine(Order order, Product product, int quantity, BigDecimal unitPrice, List<String> removedIngredients) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.removedIngredients.addAll(removedIngredients);
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
