package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Tabla intermedia producto-ingrediente con la cantidad usada (en la unidad del ingrediente). */
@Entity
@Table(name = "product_ingredients",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "ingredient_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    public ProductIngredient(Product product, Ingredient ingredient, BigDecimal quantity) {
        this.product = product;
        this.ingredient = ingredient;
        this.quantity = quantity;
    }
}
