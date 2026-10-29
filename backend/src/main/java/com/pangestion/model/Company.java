package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalTime;

/**
 * Empresa (panaderia) que usa la aplicacion. Cada empresa tiene sus propios productos, categorias,
 * ingredientes, clientes, pedidos y usuarios, y no ve los de las demas.
 * Tambien es el punto de recogida: define su horario y la capacidad de cada franja.
 */
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

    /** Panaderia, bar o restaurante. El valor por defecto permite anadir la columna a una BD con datos. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PANADERIA'")
    @Column(name = "business_type", nullable = false, length = 20)
    private BusinessType type = BusinessType.PANADERIA;

    /** CIF o NIF. */
    @Column(name = "tax_id", nullable = false, unique = true, length = 20)
    private String taxId;

    @Column(length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    /** Duracion de cada franja de recogida en minutos. */
    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 30;

    /** Pedidos maximos que se aceptan en una misma franja. */
    @Column(name = "orders_per_slot", nullable = false)
    private int ordersPerSlot = 5;

    /** Si es false no acepta pedidos ni aparece en la web publica. */
    @Column(nullable = false)
    private boolean active = true;

    /** Hace envios a domicilio: la web y el panel ofrecen "a domicilio" ademas de "recoger". */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean delivery;

    /** Logo del negocio (ruta relativa en el almacen de imagenes: {empresa}/logo/{aleatorio}); null = sin logo. */
    @Column(name = "logo_path")
    private String logoPath;
}
