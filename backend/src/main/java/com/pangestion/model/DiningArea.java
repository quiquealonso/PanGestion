package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Zona de un bar o restaurante (terraza, interior, salon...). Es una cuadricula de width x height celdas
 * donde se colocan sus mesas.
 */
@Entity
@Table(name = "dining_areas", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class DiningArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 60)
    private String name;

    /** Columnas de la cuadricula. */
    @Column(nullable = false)
    private int width;

    /** Filas de la cuadricula. */
    @Column(nullable = false)
    private int height;

    /** Posicion entre las zonas de la empresa (0 = primera). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "area", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<DiningTable> tables = new ArrayList<>();

    /** La mesa cabe entera dentro de la cuadricula. */
    public boolean fits(DiningTable table) {
        return table.getX() >= 0 && table.getY() >= 0
                && table.getX() + table.getWidth() <= width
                && table.getY() + table.getHeight() <= height;
    }

    /** Otra mesa de la zona que ocupa alguna celda de la mesa indicada (ella misma no cuenta), o null. */
    public DiningTable overlapping(DiningTable table) {
        return tables.stream()
                .filter(other -> other != table && (table.getId() == null || !table.getId().equals(other.getId())))
                .filter(table::overlaps)
                .findFirst()
                .orElse(null);
    }
}
