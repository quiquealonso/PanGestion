package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Empresa cliente (cafeterias, hoteles, restaurantes...) con sus datos fiscales. */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** CIF o NIF. */
    @Column(name = "tax_id", nullable = false, unique = true, length = 20)
    private String taxId;

    @Column(length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String address;
}
