package com.pangestion.dto;

import com.pangestion.model.Station;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Informe de sala de un periodo (bares y restaurantes): lo cobrado, las mesas cerradas y lo que se pidio en ellas.
 * Una "mesa" es un servicio cerrado (aunque ocupara varias mesas juntas).
 */
public record DiningReportDto(
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        BigDecimal cash,
        BigDecimal card,
        int tables,
        int guests,
        /** revenue / tables */
        BigDecimal avgTicket,
        /** revenue / guests */
        BigDecimal avgPerGuest,
        /** Minutos medios que dura una mesa (de abrir a cerrar) */
        long avgMinutes,
        List<DayRow> byDay,
        List<HourRow> byHour,
        List<ProductRow> topProducts,
        List<WaiterRow> byWaiter,
        List<TableRow> byTable,
        List<KitchenRow> kitchen) {

    /** Mesas cerradas y facturado (suma de sus cuentas) de un dia. */
    public record DayRow(LocalDate date, int tables, int guests, BigDecimal revenue) {
    }

    /** Mesas que se abrieron en esa hora (0-23) y lo que facturaron. */
    public record HourRow(int hour, int tables, BigDecimal revenue) {
    }

    public record ProductRow(String name, int quantity, BigDecimal revenue) {
    }

    public record WaiterRow(String name, int tables, int guests, BigDecimal revenue, BigDecimal avgTicket) {
    }

    /** Uso de cada mesa: cuantas veces se ocupo y lo que facturo. */
    public record TableRow(String table, int uses, BigDecimal revenue) {
    }

    /** Tiempo de preparacion (de pedirlo a listo) por estacion. */
    public record KitchenRow(Station station, int items, long avgMinutes, long maxMinutes) {
    }
}
