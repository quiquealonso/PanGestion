package com.pangestion.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas del servicio de una mesa: rondas, total, nombre de las mesas juntas y comensales usados. */
@Tag("reglas-negocio") // S17: entra en el CI
@DisplayName("Unidad · TableSession / ServiceLine")
class TableSessionModelTest {

    private static Product product(String price) {
        Product product = new Product();
        product.setName("Caña");
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private static DiningTable table(String name) {
        DiningTable table = new DiningTable();
        table.setName(name);
        return table;
    }

    @Test
    @DisplayName("cada ronda numera sus líneas; el total suma precio × cantidad")
    void roundsAndTotal() {
        TableSession session = new TableSession();
        int first = session.addRound(List.of(new ServiceLine(product("2.00"), 3, null, List.of())));
        int second = session.addRound(List.of(new ServiceLine(product("4.50"), 2, null, List.of())));

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(session.getLines()).extracting(ServiceLine::getRound).containsExactly(1, 2);
        assertThat(session.getTotal()).isEqualByComparingTo("15.00");
        assertThat(session.getItemCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("el precio se copia al pedir: cambiar luego el producto no cambia la cuenta")
    void priceIsCopied() {
        Product cana = product("2.00");
        ServiceLine line = new ServiceLine(cana, 1, null, List.of());
        cana.setPrice(new BigDecimal("3.00"));
        assertThat(line.getSubtotal()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("mesas juntas: el nombre las ordena (2 antes que 10)")
    void tableLabel() {
        TableSession session = new TableSession();
        session.occupy(new LinkedHashSet<>(List.of(table("10"), table("2"), table("T1"))));
        assertThat(session.getTableLabel()).isEqualTo("2 + 10 + T1");
    }

    @Test
    @DisplayName("reparto entre comensales: el total a partes iguales; el céntimo sobrante, al comensal 1")
    void guestShares() {
        TableSession session = new TableSession();
        session.setGuests(3);
        session.addRound(List.of(
                new ServiceLine(product("10.00"), 1, null, List.of()),
                new ServiceLine(product("5.00"), 1, null, List.of())));

        var shares = session.guestShares();
        assertThat(shares).containsOnlyKeys(1, 2, 3);
        assertThat(shares.get(1)).isEqualByComparingTo("5.00");
        assertThat(shares.get(2)).isEqualByComparingTo("5.00");

        session.addRound(List.of(new ServiceLine(product("1.00"), 1, null, List.of())));
        shares = session.guestShares();
        assertThat(shares.get(1)).isEqualByComparingTo("5.34");
        assertThat(shares.get(2)).isEqualByComparingTo("5.33");
        assertThat(shares.get(3)).isEqualByComparingTo("5.33");
        assertThat(shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(session.getTotal());
    }

    @Test
    @DisplayName("pendiente = total − cobrado; un comensal que paga queda marcado")
    void pendingAndPaidGuests() {
        TableSession session = new TableSession();
        session.setGuests(2);
        session.addRound(List.of(new ServiceLine(product("8.00"), 1, null, List.of())));
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("4.00"));
        payment.setGuest(2);
        session.getPayments().add(payment);

        assertThat(session.getPaid()).isEqualByComparingTo("4.00");
        assertThat(session.getPending()).isEqualByComparingTo("4.00");
        assertThat(session.guestHasPaid(2)).isTrue();
        assertThat(session.guestHasPaid(1)).isFalse();
    }

    @Test
    @DisplayName("cerrar libera las mesas pero conserva su nombre")
    void close() {
        TableSession session = new TableSession();
        session.occupy(new LinkedHashSet<>(List.of(table("4"))));
        session.close();
        assertThat(session.isOpen()).isFalse();
        assertThat(session.getTables()).isEmpty();
        assertThat(session.getTableLabel()).isEqualTo("4");
        assertThat(session.getClosedAt()).isNotNull();
    }
}
