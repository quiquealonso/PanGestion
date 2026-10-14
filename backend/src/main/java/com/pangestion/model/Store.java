package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** Tienda o punto de recogida. Define el horario y la capacidad de cada franja. */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(length = 20)
    private String phone;

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

    @Column(nullable = false)
    private boolean active = true;
}
