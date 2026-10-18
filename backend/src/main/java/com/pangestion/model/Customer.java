package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Cliente de una panaderia. Si es un negocio, guarda su razon social y CIF (opcionales). */
@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa (panaderia) a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Razon social, si el cliente es un negocio (cafeteria, hotel...). Nulo = particular. */
    @Column(name = "business_name", length = 120)
    private String businessName;

    /** CIF/NIF para facturar, si lo da. */
    @Column(name = "tax_id", length = 20)
    private String taxId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
