package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Materia prima con control de existencias y coste. */
@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Unidad de medida: kg, g, l, ud... */
    @Column(nullable = false, length = 10)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal stock = BigDecimal.ZERO;

    /** Por debajo de este valor el ingrediente se considera bajo stock. */
    @Column(name = "min_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    /** Coste por unidad de medida. */
    @Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(length = 100)
    private String allergen;

    public boolean isLowStock() {
        return stock.compareTo(minStock) <= 0;
    }
}
