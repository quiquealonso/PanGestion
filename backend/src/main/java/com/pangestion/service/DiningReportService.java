package com.pangestion.service;

import com.pangestion.dto.DiningReportDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.model.PaymentMethod;
import com.pangestion.model.ServiceLine;
import com.pangestion.model.Station;
import com.pangestion.model.TableSession;
import com.pangestion.repository.PaymentRepository;
import com.pangestion.repository.TableSessionRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Informe de sala de un periodo. Lo cobrado sale de los pagos (por su fecha); el resto, de las mesas
 * cerradas en el periodo y de lo que se pidio en ellas.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiningReportService {

    /** Periodo maximo de un informe. */
    private static final long MAX_DAYS = 366;

    private final TableSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUser currentUser;

    public DiningReportDto report(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException("La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BusinessException("El informe abarca como maximo un año");
        }
        Long companyId = currentUser.companyId();
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.totalsByMethod(companyId, start, end)) {
            if (row[0] == PaymentMethod.EFECTIVO) cash = (BigDecimal) row[1];
            else card = (BigDecimal) row[1];
        }
        BigDecimal revenue = cash.add(card);

        List<TableSession> sessions = sessionRepository.findClosedBetween(companyId, start, end);
        int tables = sessions.size();
        int guests = sessions.stream().mapToInt(TableSession::getGuests).sum();
        BigDecimal billed = sessions.stream().map(TableSession::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long avgMinutes = tables == 0 ? 0 : Math.round(sessions.stream()
                .mapToLong(s -> Duration.between(s.getOpenedAt(), s.getClosedAt()).toMinutes()).average().orElse(0));

        return new DiningReportDto(from, to, revenue, cash, card, tables, guests,
                divide(billed, tables), divide(billed, guests), avgMinutes,
                byDay(sessions, from, to), byHour(sessions), topProducts(sessions), byWaiter(sessions),
                byTable(sessions), kitchen(sessions));
    }

    /** Todos los dias del periodo, aunque no haya mesas (para que el grafico no tenga huecos). */
    private static List<DiningReportDto.DayRow> byDay(List<TableSession> sessions, LocalDate from, LocalDate to) {
        Map<LocalDate, List<TableSession>> days = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, new ArrayList<>());
        }
        sessions.forEach(s -> days.computeIfAbsent(s.getClosedAt().toLocalDate(), k -> new ArrayList<>()).add(s));
        return days.entrySet().stream()
                .map(e -> new DiningReportDto.DayRow(e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToInt(TableSession::getGuests).sum(), total(e.getValue())))
                .toList();
    }

    /** Horas con alguna mesa abierta, de la primera a la ultima. */
    private static List<DiningReportDto.HourRow> byHour(List<TableSession> sessions) {
        TreeMap<Integer, List<TableSession>> hours = new TreeMap<>();
        sessions.forEach(s -> hours.computeIfAbsent(s.getOpenedAt().getHour(), k -> new ArrayList<>()).add(s));
        if (hours.isEmpty()) {
            return List.of();
        }
        List<DiningReportDto.HourRow> rows = new ArrayList<>();
        for (int h = hours.firstKey(); h <= hours.lastKey(); h++) {
            List<TableSession> list = hours.getOrDefault(h, List.of());
            rows.add(new DiningReportDto.HourRow(h, list.size(), total(list)));
        }
        return rows;
    }

    /** Los 10 productos con mas unidades. */
    private static List<DiningReportDto.ProductRow> topProducts(List<TableSession> sessions) {
        Map<String, int[]> quantities = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (ServiceLine line : sessions.stream().flatMap(s -> s.getLines().stream()).toList()) {
            String name = line.getProduct().getName();
            quantities.computeIfAbsent(name, k -> new int[1])[0] += line.getQuantity();
            amounts.merge(name, line.getSubtotal(), BigDecimal::add);
        }
        return quantities.entrySet().stream()
                .map(e -> new DiningReportDto.ProductRow(e.getKey(), e.getValue()[0], amounts.get(e.getKey())))
                .sorted(Comparator.comparingInt(DiningReportDto.ProductRow::quantity).reversed()
                        .thenComparing(DiningReportDto.ProductRow::name))
                .limit(10)
                .toList();
    }

    private static List<DiningReportDto.WaiterRow> byWaiter(List<TableSession> sessions) {
        Map<String, List<TableSession>> waiters = new TreeMap<>();
        sessions.forEach(s -> waiters.computeIfAbsent(s.getWaiter() == null ? "Sin camarero" : s.getWaiter().getName(),
                k -> new ArrayList<>()).add(s));
        return waiters.entrySet().stream()
                .map(e -> {
                    BigDecimal revenue = total(e.getValue());
                    return new DiningReportDto.WaiterRow(e.getKey(), e.getValue().size(),
                            e.getValue().stream().mapToInt(TableSession::getGuests).sum(), revenue,
                            divide(revenue, e.getValue().size()));
                })
                .sorted(Comparator.comparing(DiningReportDto.WaiterRow::revenue).reversed())
                .toList();
    }

    /** Cada mesa por su nombre; si estuvo junta con otras, lo facturado se reparte entre ellas. */
    private static List<DiningReportDto.TableRow> byTable(List<TableSession> sessions) {
        Map<String, int[]> uses = new TreeMap<>(Comparator.comparing((String n) -> n.length()).thenComparing(Comparator.naturalOrder()));
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (TableSession session : sessions) {
            String[] names = session.getTableLabel().split(" \\+ ");
            BigDecimal share = divide(session.getTotal(), names.length);
            for (String name : names) {
                uses.computeIfAbsent(name, k -> new int[1])[0]++;
                amounts.merge(name, share, BigDecimal::add);
            }
        }
        return uses.entrySet().stream()
                .map(e -> new DiningReportDto.TableRow(e.getKey(), e.getValue()[0], amounts.get(e.getKey())))
                .toList();
    }

    /** Tiempo medio y maximo de pedido a listo, por estacion (lo que no se prepara no cuenta). */
    private static List<DiningReportDto.KitchenRow> kitchen(List<TableSession> sessions) {
        Map<Station, List<Long>> times = new TreeMap<>();
        sessions.stream().flatMap(s -> s.getLines().stream()).forEach(line -> {
            Long minutes = line.minutesToReady();
            if (minutes != null) times.computeIfAbsent(line.getStation(), k -> new ArrayList<>()).add(minutes);
        });
        return times.entrySet().stream()
                .map(e -> new DiningReportDto.KitchenRow(e.getKey(), e.getValue().size(),
                        Math.round(e.getValue().stream().mapToLong(Long::longValue).average().orElse(0)),
                        e.getValue().stream().mapToLong(Long::longValue).max().orElse(0)))
                .toList();
    }

    private static BigDecimal total(List<TableSession> sessions) {
        return sessions.stream().map(TableSession::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal divide(BigDecimal amount, int count) {
        return count == 0 ? BigDecimal.ZERO : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
