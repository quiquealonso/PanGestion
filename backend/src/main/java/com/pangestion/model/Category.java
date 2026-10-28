package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Categoria del catalogo (panes, bolleria, dulces...). */
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa (panaderia) a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 80)
    private String name;

    /** Color en formato hexadecimal, por ejemplo #F59E0B. */
    @Column(nullable = false, length = 7)
    private String color;

    @Column(length = 255)
    private String description;

    /** Posicion en el catalogo (0 = primera). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Donde se preparan sus productos (bares y restaurantes). null = lo decide cada producto.
     * Si se indica, todos sus productos pasan a esa estacion (Bebidas → BARRA: la cocina no las ve).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Station station;

    /** Posicion en la pantalla de cocina (0 = arriba): los entrantes antes que los principales. Aparte del orden del catalogo. */
    @Column(name = "kitchen_order", nullable = false, columnDefinition = "integer default 0 not null")
    private int kitchenOrder;
}
