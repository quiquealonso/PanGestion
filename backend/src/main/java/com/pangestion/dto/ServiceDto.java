package com.pangestion.dto;

import com.pangestion.model.DiningArea;
import com.pangestion.model.DiningTable;
import com.pangestion.model.ItemStatus;
import com.pangestion.model.Payment;
import com.pangestion.model.PaymentMethod;
import com.pangestion.model.ServiceLine;
import com.pangestion.model.SessionStatus;
import com.pangestion.model.Station;
import com.pangestion.model.TableSession;
import com.pangestion.model.TableShape;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Servicio de mesas (camareros): mesas abiertas, comensales y comandas por rondas. */
public final class ServiceDto {

    private ServiceDto() {
    }

    /** Abrir mesa: una o varias mesas juntas. */
    public record OpenRequest(
            @NotEmpty List<Long> tableIds,
            @Min(1) @Max(99) int guests,
            @Size(max = 255) String notes) {
    }

    public record UpdateRequest(
            @Min(1) @Max(99) int guests,
            @Size(max = 255) String notes) {
    }

    /** Mesas que ocupa: sirve para juntar, separar o cambiar de mesa. */
    public record TablesRequest(@NotEmpty List<Long> tableIds) {
    }

    /** Producto de una ronda (de toda la mesa: no se apunta de que comensal es). */
    public record LineRequest(
            @NotNull Long productId,
            @Min(1) @Max(99) int quantity,
            @Size(max = 120) String notes,
            List<Long> removedIngredientIds,
            /** Extras a anadir ("con huevo"), con su precio */
            List<Long> addedIngredientIds) {
    }

    /** Ronda que se envia a cocina. */
    public record RoundRequest(@NotEmpty List<@Valid LineRequest> lines) {
    }

    /**
     * Cobro. guest = comensal que paga su parte (el importe debe ser justo esa parte); label = texto para la caja
     * ("Parte 1 de 3"...). cashGiven = efectivo entregado, para el cambio. closeWhenPaid = liberar la mesa si queda pagada.
     */
    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull PaymentMethod method,
            @DecimalMin("0.00") BigDecimal cashGiven,
            @Min(1) @Max(99) Integer guest,
            @Size(max = 60) String label,
            boolean closeWhenPaid) {
    }

    public record PaymentResponse(Long id, BigDecimal amount, PaymentMethod method, Integer guest, String label,
                                  BigDecimal cashGiven, BigDecimal change, String cashierName, LocalDateTime paidAt) {

        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getAmount(), payment.getMethod(), payment.getGuest(),
                    payment.getLabel(), payment.getCashGiven(), payment.getChange(),
                    payment.getCashier() == null ? null : payment.getCashier().getName(), payment.getPaidAt());
        }
    }

    /** Parte de un comensal al dividir la cuenta "por comensal". */
    public record GuestShare(int guest, BigDecimal amount, boolean paid) {
    }

    /** Cuenta de la mesa para cobrarla: total, cobrado, pendiente y lo que toca a cada comensal. */
    public record BillResponse(Long sessionId, BigDecimal total, BigDecimal paid, BigDecimal pending,
                               List<GuestShare> guests, List<PaymentResponse> payments) {

        public static BillResponse from(TableSession session) {
            return new BillResponse(session.getId(), session.getTotal(), session.getPaid(), session.getPending(),
                    session.guestShares().entrySet().stream()
                            .map(e -> new GuestShare(e.getKey(), e.getValue(), session.guestHasPaid(e.getKey())))
                            .toList(),
                    session.getPayments().stream().map(PaymentResponse::from).toList());
        }
    }

    /** Mesa ya cerrada, para la caja del dia. */
    public record ClosedSession(Long id, String tableLabel, int guests, int itemCount, BigDecimal total, BigDecimal paid,
                                String waiterName, LocalDateTime openedAt, LocalDateTime closedAt) {

        static ClosedSession from(TableSession s) {
            return new ClosedSession(s.getId(), s.getTableLabel(), s.getGuests(), s.getItemCount(), s.getTotal(),
                    s.getPaid(), s.getWaiter() == null ? null : s.getWaiter().getName(), s.getOpenedAt(), s.getClosedAt());
        }
    }

    /** Caja de un dia: lo cobrado en efectivo y con tarjeta, y las mesas cerradas. */
    public record DayResponse(LocalDate date, BigDecimal cash, BigDecimal card, BigDecimal total, int tables,
                              List<ClosedSession> sessions) {

        public static DayResponse of(LocalDate date, BigDecimal cash, BigDecimal card, List<TableSession> closed) {
            return new DayResponse(date, cash, card, cash.add(card), closed.size(),
                    closed.stream().map(ClosedSession::from).toList());
        }
    }

    public record TableRef(Long id, String name, Long areaId) {

        static TableRef from(DiningTable table) {
            return new TableRef(table.getId(), table.getName(), table.getArea().getId());
        }
    }

    public record LineResponse(Long id, Long productId, String productName, String color, int quantity,
                               BigDecimal unitPrice, BigDecimal subtotal, String notes,
                               List<String> removedIngredients, List<IngredientDto.LineExtraResponse> extras,
                               int round, LocalDateTime createdAt,
                               Station station, ItemStatus status, LocalDateTime statusChangedAt) {

        static LineResponse from(ServiceLine line) {
            var product = line.getProduct();
            String color = product.getColor() != null ? product.getColor() : product.getCategory().getColor();
            return new LineResponse(line.getId(), product.getId(), product.getName(), color, line.getQuantity(),
                    line.getUnitPrice(), line.getSubtotal(), line.getNotes(),
                    List.copyOf(line.getRemovedIngredients()), IngredientDto.LineExtraResponse.from(line.getExtras()),
                    line.getRound(), line.getCreatedAt(),
                    line.getStation(), line.getStatus(), line.getStatusChangedAt());
        }
    }

    public record SessionResponse(Long id, SessionStatus status, String tableLabel, List<TableRef> tables, int guests,
                                  String notes, String waiterName, int rounds, int itemCount, BigDecimal total,
                                  BigDecimal paid, BigDecimal pending,
                                  LocalDateTime openedAt, LocalDateTime closedAt, List<LineResponse> lines,
                                  List<PaymentResponse> payments) {

        public static SessionResponse from(TableSession session) {
            return new SessionResponse(session.getId(), session.getStatus(), session.getTableLabel(),
                    session.getTables().stream().map(TableRef::from).sorted(Comparator.comparing(TableRef::name)).toList(),
                    session.getGuests(), session.getNotes(),
                    session.getWaiter() == null ? null : session.getWaiter().getName(),
                    session.getRounds(), session.getItemCount(), session.getTotal(),
                    session.getPaid(), session.getPending(),
                    session.getOpenedAt(), session.getClosedAt(),
                    session.getLines().stream().map(LineResponse::from).toList(),
                    session.getPayments().stream().map(PaymentResponse::from).toList());
        }
    }

    /** Resumen de la mesa ocupada para pintarla en el plano. readyCount = unidades listas para llevar a la mesa. */
    public record SessionSummary(Long id, String tableLabel, int guests, int itemCount, BigDecimal total,
                                 BigDecimal paid, LocalDateTime openedAt, int readyCount) {

        static SessionSummary from(TableSession session) {
            int ready = session.getLines().stream()
                    .filter(l -> l.getStatus() == ItemStatus.LISTO).mapToInt(ServiceLine::getQuantity).sum();
            return new SessionSummary(session.getId(), session.getTableLabel(), session.getGuests(),
                    session.getItemCount(), session.getTotal(), session.getPaid(), session.getOpenedAt(), ready);
        }
    }

    /** Mesa del plano con su servicio abierto (null = libre). */
    public record FloorTable(Long id, String name, int seats, TableShape shape,
                             int x, int y, int width, int height, SessionSummary session) {
    }

    public record FloorArea(Long id, String name, int width, int height, List<FloorTable> tables) {

        public static FloorArea from(DiningArea area, Map<Long, TableSession> openByTable) {
            return new FloorArea(area.getId(), area.getName(), area.getWidth(), area.getHeight(),
                    area.getTables().stream().map(t -> {
                        TableSession session = openByTable.get(t.getId());
                        return new FloorTable(t.getId(), t.getName(), t.getSeats(), t.getShape(), t.getX(), t.getY(),
                                t.getWidth(), t.getHeight(), session == null ? null : SessionSummary.from(session));
                    }).toList());
        }
    }
}
