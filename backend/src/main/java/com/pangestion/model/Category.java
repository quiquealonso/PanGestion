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
}
