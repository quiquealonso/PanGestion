package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cobro de una mesa. Una mesa se puede pagar de una vez, por comensales o a partes iguales:
 * cada pago es un Payment. guest indica el comensal que paga lo suyo (null = pago general).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TableSession session;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10) not null")
    private PaymentMethod method;

    /** Comensal que paga su parte; null = pago del total o de una parte igual. */
    private Integer guest;

    /** Texto para la caja y el ticket: "Total", "Comensal 2", "Parte 1 de 3"... */
    @Column(nullable = false, length = 60)
    private String label;

    /** Efectivo entregado (para calcular el cambio); null con tarjeta. */
    @Column(name = "cash_given", precision = 10, scale = 2)
    private BigDecimal cashGiven;

    /** Quien cobro. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private LocalDateTime paidAt;

    @PrePersist
    void onCreate() {
        if (paidAt == null) paidAt = LocalDateTime.now();
    }

    /** Cambio a devolver (0 con tarjeta o pago exacto). */
    public BigDecimal getChange() {
        return cashGiven == null ? BigDecimal.ZERO : cashGiven.subtract(amount);
    }
}
