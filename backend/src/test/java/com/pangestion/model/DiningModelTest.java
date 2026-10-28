package com.pangestion.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas del plano de la sala: una mesa cabe en su zona y no pisa a otra. */
@Tag("reglas-negocio") // S17: entra en el CI
@DisplayName("Unidad · DiningArea / DiningTable")
class DiningModelTest {

    private static DiningTable table(long id, int x, int y, int width, int height) {
        DiningTable table = new DiningTable();
        table.setId(id);
        table.setName("M" + id);
        table.setX(x);
        table.setY(y);
        table.setWidth(width);
        table.setHeight(height);
        return table;
    }

    private static DiningArea area(int width, int height, DiningTable... tables) {
        DiningArea area = new DiningArea();
        area.setWidth(width);
        area.setHeight(height);
        for (DiningTable table : tables) {
            table.setArea(area);
            area.getTables().add(table);
        }
        return area;
    }

    @Test
    @DisplayName("cabe: tocando el borde sí; pasándose una celda o en negativo, no")
    void fits() {
        DiningArea area = area(10, 6);
        assertThat(area.fits(table(1, 0, 0, 2, 2))).isTrue();
        assertThat(area.fits(table(1, 8, 4, 2, 2))).isTrue();
        assertThat(area.fits(table(1, 9, 4, 2, 2))).isFalse();
        assertThat(area.fits(table(1, 8, 5, 2, 2))).isFalse();
        assertThat(area.fits(table(1, -1, 0, 2, 2))).isFalse();
    }

    @Test
    @DisplayName("se solapan si comparten una celda; pegadas lado con lado, no")
    void overlaps() {
        DiningTable a = table(1, 2, 2, 3, 2);
        assertThat(a.overlaps(table(2, 4, 3, 2, 2))).isTrue();
        assertThat(a.overlaps(table(2, 3, 2, 1, 1))).isTrue();
        assertThat(a.overlaps(table(2, 5, 2, 2, 2))).isFalse();
        assertThat(a.overlaps(table(2, 2, 4, 3, 1))).isFalse();
    }

    @Test
    @DisplayName("la zona encuentra la mesa que pisa, pero no se cuenta a sí misma")
    void overlappingInArea() {
        DiningTable one = table(1, 0, 0, 2, 2);
        DiningTable two = table(2, 4, 0, 2, 2);
        DiningArea area = area(10, 6, one, two);

        assertThat(area.overlapping(one)).isNull();
        one.setX(3);
        assertThat(area.overlapping(one)).isSameAs(two);
        assertThat(area.overlapping(table(1, 3, 0, 1, 1))).isNull(); // mismo id que "one": es ella movida
    }
}
