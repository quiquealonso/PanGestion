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

/** Pedido de un cliente para recoger en su panaderia un dia y a una hora. */
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

    /** Panaderia a la que se hace el pedido (y donde se recoge). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "pickup_time", nullable = false)
    private LocalTime pickupTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderStatus status = OrderStatus.PENDIENTE;

    /** Recoger en el local o a domicilio. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10) default 'RECOGER' not null")
    private OrderType type = OrderType.RECOGER;

    /** Direccion de entrega (solo a domicilio). */
    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    /** Repartidor que lo lleva (a domicilio, desde que sale). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    /** Cuando salio con el repartidor. */
    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

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
        addLine(product, quantity, List.of());
    }

    /** Linea personalizada: el mismo producto quitandole algunos ingredientes (el precio no cambia). */
    public void addLine(Product product, int quantity, List<String> removedIngredients) {
        addLine(product, quantity, removedIngredients, List.of());
    }

    /** Linea con ingredientes quitados y extras anadidos: el precio es el del producto mas los extras. */
    public void addLine(Product product, int quantity, List<String> removedIngredients, List<LineExtra> extras) {
        lines.add(new OrderLine(this, product, quantity, product.getPrice(), removedIngredients, extras));
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

    /**
     * Pedido para llevar en un bar o restaurante: su estado sigue al de sus productos en cocina.
     * Todos listos = LISTO; alguno empezado = EN_PROCESO; ninguno = PENDIENTE.
     * No toca pedidos ya entregados o cancelados.
     */
    public void syncStatusFromLines() {
        if (status == OrderStatus.TRANSPORTANDO || status == OrderStatus.ENTREGADO || status == OrderStatus.CANCELADO
                || lines.isEmpty()) {
            return;
        }
        if (lines.stream().allMatch(l -> l.getStatus().compareTo(ItemStatus.LISTO) >= 0)) {
            status = OrderStatus.LISTO;
        } else if (lines.stream().anyMatch(l -> l.getStatus() != ItemStatus.PENDIENTE)) {
            status = OrderStatus.EN_PROCESO;
        } else {
            status = OrderStatus.PENDIENTE;
        }
    }

    public int getItemCount() {
        return lines.stream().mapToInt(OrderLine::getQuantity).sum();
    }
}
