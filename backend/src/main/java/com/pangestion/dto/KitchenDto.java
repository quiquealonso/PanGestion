package com.pangestion.dto;

import com.pangestion.model.Category;
import com.pangestion.model.ItemStatus;
import com.pangestion.model.LineExtra;
import com.pangestion.model.OrderLine;
import com.pangestion.model.Product;
import com.pangestion.model.ServiceLine;
import com.pangestion.model.Station;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pantalla de cocina y barra: tickets (una ronda de una mesa o un pedido para llevar) con sus productos,
 * cada uno con su propio estado.
 */
public final class KitchenDto {

    private KitchenDto() {
    }

    /** De donde viene el producto: una mesa (servicio de mesas) o un pedido para llevar. */
    public enum Source {
        MESA,
        LLEVAR
    }

    /**
     * Un producto del ticket. La categoria (nombre y color) distingue dos productos que se llaman igual;
     * categoryOrder es su posicion en cocina (los entrantes primero). extras = lo que se anade, removedIngredients = lo que se quita.
     */
    public record Item(Source source, Long id, String productName, String abbreviation, String categoryName,
                       String categoryColor, int categoryOrder, int quantity, String notes, List<String> removedIngredients,
                       List<String> extras, Station station, ItemStatus status, LocalDateTime statusChangedAt) {

        public static Item from(ServiceLine line) {
            Product product = line.getProduct();
            return new Item(Source.MESA, line.getId(), product.getName(), product.getAbbreviation(),
                    product.getCategory().getName(), product.getCategory().getColor(), order(product.getCategory()),
                    line.getQuantity(), line.getNotes(), List.copyOf(line.getRemovedIngredients()),
                    LineExtra.names(line.getExtras()), line.getStation(), line.getStatus(), line.getStatusChangedAt());
        }

        public static Item from(OrderLine line) {
            Product product = line.getProduct();
            return new Item(Source.LLEVAR, line.getId(), product.getName(), product.getAbbreviation(),
                    product.getCategory().getName(), product.getCategory().getColor(), order(product.getCategory()),
                    line.getQuantity(), null, List.copyOf(line.getRemovedIngredients()),
                    LineExtra.names(line.getExtras()), line.getStation(), line.getStatus(), line.getStatusChangedAt());
        }

        /** Posicion de la categoria en cocina; a igualdad, la del catalogo. */
        private static int order(Category category) {
            return category.getKitchenOrder() * 1000 + category.getSortOrder();
        }
    }

    /**
     * Ticket de cocina. refId = servicio de mesa o pedido; round = ronda (solo mesas).
     * time = cuando se pidio (mesas) o la hora de recogida (para llevar); ordena la pantalla.
     */
    public record Ticket(Source source, Long refId, Integer round, String label, String detail, String notes,
                         LocalDateTime time, List<Item> items) {
    }

    /** units = cuantas unidades de la linea se mueven (null = todas). Tocar un producto mueve una. */
    public record ItemRef(@NotNull Source source, @NotNull Long id, @Positive Integer units) {
    }

    /** Mover uno o varios productos al mismo estado. */
    public record UpdateRequest(@NotEmpty List<@Valid ItemRef> items, @NotNull ItemStatus status) {
    }
}
