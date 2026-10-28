package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mesa del plano de una zona. Ocupa un rectangulo de celdas: desde (x, y) con width x height celdas.
 */
@Entity
@Table(name = "dining_tables", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa, para buscar y validar mesas sin pasar por la zona. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private DiningArea area;

    /** Nombre corto que ven los camareros: "1", "T4", "Barra 2"... */
    @Column(nullable = false, length = 20)
    private String name;

    /** Plazas (comensales habituales). */
    @Column(nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TableShape shape = TableShape.RECT;

    @Column(name = "pos_x", nullable = false)
    private int x;

    @Column(name = "pos_y", nullable = false)
    private int y;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** Comparte alguna celda con la otra mesa. */
    public boolean overlaps(DiningTable other) {
        return x < other.x + other.width && other.x < x + width
                && y < other.y + other.height && other.y < y + height;
    }
}
