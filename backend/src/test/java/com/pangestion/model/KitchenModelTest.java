package com.pangestion.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de cocina: estado de cada producto, estacion y estado del pedido para llevar. */
@Tag("reglas-negocio") // S17: entra en el CI
@DisplayName("Unidad · ItemStatus / estado por producto")
class KitchenModelTest {

    private static Product product(Station station) {
        Product product = new Product();
        product.setName("Plato");
        product.setPrice(new BigDecimal("5.00"));
        product.setStation(station);
        return product;
    }

    @Test
    @DisplayName("solo se avanza o retrocede un paso")
    void transitions() {
        assertThat(ItemStatus.PENDIENTE.canMoveTo(ItemStatus.PREPARANDO)).isTrue();
        assertThat(ItemStatus.LISTO.canMoveTo(ItemStatus.PREPARANDO)).isTrue();
        assertThat(ItemStatus.LISTO.canMoveTo(ItemStatus.SERVIDO)).isTrue();
        assertThat(ItemStatus.PENDIENTE.canMoveTo(ItemStatus.LISTO)).isFalse();
        assertThat(ItemStatus.PENDIENTE.canMoveTo(ItemStatus.SERVIDO)).isFalse();
        assertThat(ItemStatus.PENDIENTE.canMoveTo(ItemStatus.PENDIENTE)).isFalse();
    }

    @Test
    @DisplayName("la línea copia la estación del producto; lo que no se prepara sale ya listo")
    void initialStatusByStation() {
        ServiceLine plate = new ServiceLine(product(Station.COCINA), 1, null, List.of());
        ServiceLine bread = new ServiceLine(product(Station.NINGUNA), 1, null, List.of());
        assertThat(plate.getStatus()).isEqualTo(ItemStatus.PENDIENTE);
        assertThat(bread.getStatus()).isEqualTo(ItemStatus.LISTO);
        assertThat(bread.getStation()).isEqualTo(Station.NINGUNA);
    }

    @Test
    @DisplayName("moveTo apunta la hora y rechaza saltos")
    void moveTo() {
        ServiceLine line = new ServiceLine(product(Station.BARRA), 1, null, List.of());
        line.moveTo(ItemStatus.PREPARANDO);
        assertThat(line.getStatus()).isEqualTo(ItemStatus.PREPARANDO);
        assertThat(line.getStatusChangedAt()).isNotNull();
        assertThatThrownBy(() -> line.moveTo(ItemStatus.SERVIDO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("al quedar listo se apunta la hora; si se devuelve a preparación, se borra")
    void readyAt() {
        ServiceLine line = new ServiceLine(product(Station.COCINA), 1, null, List.of());
        line.moveTo(ItemStatus.PREPARANDO);
        assertThat(line.getReadyAt()).isNull();
        line.moveTo(ItemStatus.LISTO);
        assertThat(line.getReadyAt()).isNotNull();
        line.moveTo(ItemStatus.PREPARANDO);
        assertThat(line.getReadyAt()).isNull();
        assertThat(line.minutesToReady()).isNull();
    }

    @Test
    @DisplayName("pedido para llevar: su estado sigue al de sus productos; entregado o cancelado no cambia")
    void orderFollowsLines() {
        Order order = new Order();
        order.addLine(product(Station.COCINA), 1);
        order.addLine(product(Station.COCINA), 1);
        OrderLine first = order.getLines().get(0);
        OrderLine second = order.getLines().get(1);

        order.syncStatusFromLines();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDIENTE);

        first.moveTo(ItemStatus.PREPARANDO);
        order.syncStatusFromLines();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EN_PROCESO);

        first.moveTo(ItemStatus.LISTO);
        second.moveTo(ItemStatus.PREPARANDO);
        second.moveTo(ItemStatus.LISTO);
        order.syncStatusFromLines();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.LISTO);

        order.setStatus(OrderStatus.ENTREGADO);
        second.moveTo(ItemStatus.PREPARANDO);
        order.syncStatusFromLines();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ENTREGADO);
    }

    @Test
    @DisplayName("separar unidades: la nueva línea copia ronda, notas y estado; la original se queda con el resto")
    void split() {
        Product plate = product(Station.COCINA);
        plate.setId(1L);
        ServiceLine line = new ServiceLine(plate, 3, "poco hecho", List.of("cebolla"));
        line.setRound(2);

        ServiceLine one = line.split(1);
        assertThat(line.getQuantity()).isEqualTo(2);
        assertThat(one.getQuantity()).isEqualTo(1);
        assertThat(one.getRound()).isEqualTo(2);
        assertThat(one.getNotes()).isEqualTo("poco hecho");
        assertThat(one.getStatus()).isEqualTo(ItemStatus.PENDIENTE);
        assertThat(one.sameDishAs(line)).isTrue();
        assertThat(line.getSubtotal().add(one.getSubtotal())).isEqualByComparingTo("15.00");

        one.setNotes("muy hecho");
        assertThat(one.sameDishAs(line)).isFalse();
        assertThatThrownBy(() -> line.split(2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("extras: suman al precio de la línea; 'con huevo' y 'sin nada' no se juntan")
    void extras() {
        Product plate = product(Station.COCINA);
        plate.setId(1L);
        ServiceLine plain = new ServiceLine(plate, 1, null, List.of());
        ServiceLine withEgg = new ServiceLine(plate, 2, null, List.of(),
                List.of(new LineExtra("Huevo", new BigDecimal("1.00")), new LineExtra("Bacon", new BigDecimal("1.50"))));
        assertThat(withEgg.getUnitPrice()).isEqualByComparingTo("7.50");
        assertThat(withEgg.getSubtotal()).isEqualByComparingTo("15.00");
        assertThat(withEgg.sameDishAs(plain)).isFalse();
        assertThat(withEgg.split(1).sameDishAs(withEgg)).isTrue();
    }

    @Test
    @DisplayName("barra: de pendiente a listo (y vuelta) sin pasar por preparando; la cocina no se lo salta")
    void barSkipsPreparing() {
        assertThat(ItemStatus.PENDIENTE.canMoveTo(ItemStatus.LISTO, Station.BARRA)).isTrue();
        assertThat(ItemStatus.LISTO.canMoveTo(ItemStatus.PENDIENTE, Station.BARRA)).isTrue();
        assertThat(ItemStatus.PREPARANDO.canMoveTo(ItemStatus.LISTO, Station.BARRA)).isTrue();
        assertThat(ItemStatus.PENDIENTE.canMoveTo(ItemStatus.LISTO, Station.COCINA)).isFalse();

        ServiceLine beer = new ServiceLine(product(Station.BARRA), 2, null, List.of());
        beer.moveTo(ItemStatus.LISTO);
        assertThat(beer.getStatus()).isEqualTo(ItemStatus.LISTO);
        assertThat(beer.getReadyAt()).isNotNull();
    }
}
