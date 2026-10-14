package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Categoria del catalogo (panes, bolleria, dulces...). */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    /** Color en formato hexadecimal, por ejemplo #F59E0B. */
    @Column(nullable = false, length = 7)
    private String color;

    @Column(length = 255)
    private String description;
}
