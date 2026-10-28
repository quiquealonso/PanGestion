package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Producto pedido en una mesa. Guarda el precio del momento y la ronda en la que se pidio.
 * Es de toda la mesa: no se apunta de que comensal es (los comensales solo sirven para dividir la cuenta).
 */
@Entity
@Table(name = "service_lines")
@Getter
@Setter
@NoArgsConstructor
public class ServiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TableSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Indicaciones para cocina ("poco hecho", "sin sal"...). */
    @Column(length = 120)
    private String notes;

    /** Ingredientes quitados, por nombre (como en OrderLine). */
    @ElementCollection
    @CollectionTable(name = "service_line_removed_ingredients", joinColumns = @JoinColumn(name = "service_line_id"))
    @Column(name = "ingredient_name", nullable = false, length = 100)
    private List<String> removedIngredients = new ArrayList<>();

    /** Extras anadidos ("con huevo"), con su precio; ya van sumados en unitPrice. */
    @ElementCollection
    @CollectionTable(name = "service_line_extras", joinColumns = @JoinColumn(name = "service_line_id"))
    private List<LineExtra> extras = new ArrayList<>();

    /** Donde se prepara, copiado del producto al pedirlo (no cambia aunque luego se edite el producto). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10) default 'COCINA' not null")
    private Station station = Station.COCINA;

    /** Estado de este producto en cocina o barra. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(12) default 'PENDIENTE' not null")
    private ItemStatus status = ItemStatus.PENDIENTE;

    /** Ultimo cambio de estado (para el tiempo que lleva esperando en cocina). */
    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    /** Cuando quedo listo (para medir lo que tarda cocina o barra). Se borra si se devuelve a preparacion. */
    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    /** Ronda (envio a cocina) en la que se pidio: 1, 2, 3... */
    @Column(name = "round_number", nullable = false)
    private int round;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ServiceLine(Product product, int quantity, String notes, List<String> removedIngredients) {
        this(product, quantity, notes, removedIngredients, List.of());
    }

    /** El precio es el del producto mas sus extras. */
    public ServiceLine(Product product, int quantity, String notes, List<String> removedIngredients, List<LineExtra> extras) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice().add(LineExtra.total(extras));
        this.extras.addAll(extras);
        this.notes = notes;
        this.removedIngredients.addAll(removedIngredients);
        this.station = product.getStation() == null ? Station.COCINA : product.getStation();
        this.status = ItemStatus.initial(station);
        this.statusChangedAt = LocalDateTime.now();
    }

    /** Cambia de estado (solo al siguiente o al anterior) y apunta la hora. */
    public void moveTo(ItemStatus target) {
        if (!status.canMoveTo(target, station)) {
            throw new IllegalArgumentException(status + " no puede pasar a " + target);
        }
        status = target;
        statusChangedAt = LocalDateTime.now();
        if (target == ItemStatus.LISTO && readyAt == null) {
            readyAt = statusChangedAt;
        } else if (target.compareTo(ItemStatus.LISTO) < 0) {
            readyAt = null;
        }
    }

    /** Minutos desde que se pidio hasta que quedo listo; null si no se ha preparado (o no pasa por cocina). */
    public Long minutesToReady() {
        if (readyAt == null || createdAt == null || station == Station.NINGUNA) {
            return null;
        }
        return java.time.Duration.between(createdAt, readyAt).toMinutes();
    }

    /**
     * Separa unas unidades en una linea nueva igual (mismo precio, ronda, estado y hora de pedido);
     * esta se queda con el resto. La nueva no se anade a la mesa: lo decide quien la separa.
     */
    public ServiceLine split(int units) {
        if (units <= 0 || units >= quantity) {
            throw new IllegalArgumentException("No se pueden separar " + units + " de " + quantity);
        }
        ServiceLine copy = new ServiceLine();
        copy.session = session;
        copy.product = product;
        copy.quantity = units;
        copy.unitPrice = unitPrice;
        copy.notes = notes;
        copy.removedIngredients.addAll(removedIngredients);
        copy.extras.addAll(extras);
        copy.station = station;
        copy.status = status;
        copy.statusChangedAt = statusChangedAt;
        copy.readyAt = readyAt;
        copy.round = round;
        copy.createdAt = createdAt;
        quantity -= units;
        return copy;
    }

    /** Lo mismo pedido en la misma ronda (producto, precio, notas, ingredientes quitados y extras): se puede juntar en una linea. */
    public boolean sameDishAs(ServiceLine other) {
        return product.getId().equals(other.product.getId())
                && round == other.round
                && unitPrice.compareTo(other.unitPrice) == 0
                && Objects.equals(notes, other.notes)
                && station == other.station
                && List.copyOf(removedIngredients).equals(List.copyOf(other.removedIngredients)) // la coleccion de Hibernate no compara contenido
                && LineExtra.names(extras).equals(LineExtra.names(other.extras));
    }

    @PrePersist
    void onCreate() {
        // Una linea separada de otra conserva la hora en que se pidio
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
