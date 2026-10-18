package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ingrediente que forma parte de los productos (nueces, chocolate blanco, arroz, pollo...).
 * Sirve para saber de que esta hecho cada producto y mostrar sus alergenos; no controla stock.
 */
@Entity
@Table(name = "ingredients", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa (panaderia) a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    /** Alergeno que aporta (Gluten, Lacteos, Frutos de cascara...). Nulo si no tiene. */
    @Column(length = 100)
    private String allergen;
}
