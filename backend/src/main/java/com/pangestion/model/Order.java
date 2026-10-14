package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Pedido de un cliente para recoger en una tienda, un dia y a una hora. */
@Entity
@Table(name = "orders") // ORDER es palabra reservada en SQL
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "pickup_time", nullable = false)
    private LocalTime pickupTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderStatus status = OrderStatus.PENDIENTE;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Anade una linea copiando el precio actual del producto y recalcula el total. */
    public void addLine(Product product, int quantity) {
        lines.add(new OrderLine(this, product, quantity, product.getPrice()));
        recalculateTotal();
    }

    public void clearLines() {
        lines.clear();
        recalculateTotal();
    }

    public void recalculateTotal() {
        total = lines.stream()
                .map(OrderLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getItemCount() {
        return lines.stream().mapToInt(OrderLine::getQuantity).sum();
    }
}
