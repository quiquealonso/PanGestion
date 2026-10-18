package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/** Producto que elabora la panaderia. Solo los activos se ofrecen a los clientes. */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa (panaderia) a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 120)
    private String name;

    /** Nombre corto para tickets y hojas de produccion (por ejemplo "N-X"). */
    @Column(length = 20)
    private String abbreviation;

    @Column(length = 500)
    private String description;

    /** Ruta de la imagen en el almacenamiento (ver ImageStorage). Nulo = sin imagen. */
    @Column(name = "image_path", length = 255)
    private String imagePath;

    /** Color propio en hexadecimal. Si es nulo se usa el de la categoria. */
    @Column(length = 7)
    private String color;

    /** Posicion dentro de su categoria (0 = primero). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Ingredientes de los que esta hecho el producto. */
    @ManyToMany
    @JoinTable(name = "product_ingredients",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "ingredient_id"))
    @OrderBy("name ASC")
    private Set<Ingredient> ingredients = new LinkedHashSet<>();

    /** Color con el que se muestra: el propio o, si no tiene, el de su categoria. */
    public String getDisplayColor() {
        return color != null ? color : category.getColor();
    }

}
