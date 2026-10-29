package com.pangestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Linea de pedido. Guarda el precio unitario del momento de la compra y los ingredientes quitados. */
@Entity
@Table(name = "order_lines")
@Getter
@Setter
@NoArgsConstructor
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Ingredientes que el cliente ha pedido quitar ("pizza sin cebolla").
     * Se guarda el nombre, igual que el precio: el pedido no cambia aunque luego se renombre o borre el ingrediente.
     */
    @ElementCollection
    @CollectionTable(name = "order_line_removed_ingredients", joinColumns = @JoinColumn(name = "order_line_id"))
    @Column(name = "ingredient_name", nullable = false, length = 100)
    private List<String> removedIngredients = new ArrayList<>();

    /** Extras anadidos ("con huevo"), con su precio; ya van sumados en unitPrice. */
    @ElementCollection
    @CollectionTable(name = "order_line_extras", joinColumns = @JoinColumn(name = "order_line_id"))
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

    /** unitPrice = precio del producto; los extras se le suman. */
    public OrderLine(Order order, Product product, int quantity, BigDecimal unitPrice, List<String> removedIngredients,
                     List<LineExtra> extras) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice.add(LineExtra.total(extras));
        this.removedIngredients.addAll(removedIngredients);
        this.extras.addAll(extras);
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
    }

    /** Separa unas unidades en una linea nueva igual; esta se queda con el resto. La nueva no se anade al pedido. */
    public OrderLine split(int units) {
        if (units <= 0 || units >= quantity) {
            throw new IllegalArgumentException("No se pueden separar " + units + " de " + quantity);
        }
        OrderLine copy = new OrderLine();
        copy.order = order;
        copy.product = product;
        copy.quantity = units;
        copy.unitPrice = unitPrice;
        copy.removedIngredients.addAll(removedIngredients);
        copy.extras.addAll(extras);
        copy.station = station;
        copy.status = status;
        copy.statusChangedAt = statusChangedAt;
        quantity -= units;
        return copy;
    }

    /** Mismo producto, precio, ingredientes quitados y extras: se puede juntar en una linea. */
    public boolean sameDishAs(OrderLine other) {
        return product.getId().equals(other.product.getId())
                && unitPrice.compareTo(other.unitPrice) == 0
                && station == other.station
                && List.copyOf(removedIngredients).equals(List.copyOf(other.removedIngredients)) // la coleccion de Hibernate no compara contenido
                && LineExtra.names(extras).equals(LineExtra.names(other.extras));
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
