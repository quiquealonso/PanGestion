package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Servicio de una mesa: desde que se sientan los clientes hasta que se van. Puede ocupar varias mesas
 * juntas (solo mientras dura; el plano no cambia). Guarda los productos pedidos por rondas y por comensal.
 */
@Entity
@Table(name = "table_sessions")
@Getter
@Setter
@NoArgsConstructor
public class TableSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Camarero que abrio la mesa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    private User waiter;

    /** Mesas que ocupa mientras esta abierta. Al cerrarla se vacia y queda solo el nombre en tableLabel. */
    @ManyToMany
    @JoinTable(name = "table_session_tables",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "table_id"))
    private Set<DiningTable> tables = new LinkedHashSet<>();

    /** Nombre de las mesas para mostrarlo ("4 + 5"); se conserva al cerrar aunque luego se borre la mesa. */
    @Column(name = "table_label", nullable = false, length = 120)
    private String tableLabel;

    @Column(nullable = false)
    private int guests;

    @Column(length = 255)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionStatus status = SessionStatus.OPEN;

    /** Rondas enviadas a cocina hasta ahora. */
    @Column(nullable = false)
    private int rounds;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ServiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Payment> payments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        openedAt = LocalDateTime.now();
    }

    public boolean isOpen() {
        return status == SessionStatus.OPEN;
    }

    /** Ocupa estas mesas y actualiza el nombre que se muestra (ordenadas por nombre). */
    public void occupy(Set<DiningTable> newTables) {
        tables.clear();
        tables.addAll(newTables);
        tableLabel = newTables.stream()
                .map(DiningTable::getName)
                .sorted(Comparator.comparing((String n) -> n.length()).thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.joining(" + "));
    }

    /** Libera las mesas: el servicio queda en el historial con su nombre. */
    public void close() {
        status = SessionStatus.CLOSED;
        closedAt = LocalDateTime.now();
        tables.clear();
    }

    /** Anade una ronda de lineas y devuelve su numero. */
    public int addRound(List<ServiceLine> round) {
        rounds++;
        for (ServiceLine line : round) {
            line.setSession(this);
            line.setRound(rounds);
            lines.add(line);
        }
        return rounds;
    }

    public BigDecimal getTotal() {
        return lines.stream().map(ServiceLine::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo ya cobrado. */
    public BigDecimal getPaid() {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo que queda por cobrar (nunca negativo). */
    public BigDecimal getPending() {
        return getTotal().subtract(getPaid()).max(BigDecimal.ZERO);
    }

    /** El comensal ya ha pagado su parte. */
    public boolean guestHasPaid(int guest) {
        return payments.stream().anyMatch(p -> p.getGuest() != null && p.getGuest() == guest);
    }

    /** Algun comensal ya ha pagado su parte. */
    public boolean anyGuestHasPaid() {
        return payments.stream().anyMatch(p -> p.getGuest() != null);
    }

    /**
     * Lo que paga cada comensal si la cuenta se divide entre los comensales: el total a partes iguales.
     * Los productos son de toda la mesa (no se apunta de quien es cada uno). Los centimos que no se reparten
     * exactos los paga el comensal 1, asi la suma de las partes es siempre el total.
     */
    public Map<Integer, BigDecimal> guestShares() {
        Map<Integer, BigDecimal> shares = new TreeMap<>();
        if (guests <= 0) {
            return shares;
        }
        BigDecimal total = getTotal();
        BigDecimal each = total.divide(BigDecimal.valueOf(guests), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(each.multiply(BigDecimal.valueOf(guests)));
        for (int g = 1; g <= guests; g++) {
            shares.put(g, each.add(g == 1 ? remainder : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
        return shares;
    }

    public int getItemCount() {
        return lines.stream().mapToInt(ServiceLine::getQuantity).sum();
    }
}
