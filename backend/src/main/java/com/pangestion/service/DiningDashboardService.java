package com.pangestion.service;

import com.pangestion.dto.DiningDashboardDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.model.BusinessType;
import com.pangestion.model.ItemStatus;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.PaymentMethod;
import com.pangestion.model.ServiceLine;
import com.pangestion.model.Station;
import com.pangestion.model.TableSession;
import com.pangestion.repository.DiningTableRepository;
import com.pangestion.repository.OrderRepository;
import com.pangestion.repository.PaymentRepository;
import com.pangestion.repository.TableSessionRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Resumen para el inicio de un bar o restaurante (la panaderia usa DashboardService). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiningDashboardService {

    private final TableSessionRepository sessionRepository;
    private final DiningTableRepository tableRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    public DiningDashboardDto summary() {
        if (companyService.current().getType() == BusinessType.PANADERIA) {
            throw new BusinessException("El resumen de sala solo esta disponible para bares y restaurantes");
        }
        Long companyId = currentUser.companyId();
        LocalDate today = LocalDate.now();

        List<TableSession> open = sessionRepository.findOpen(companyId);
        List<ServiceLine> lines = open.stream().flatMap(s -> s.getLines().stream()).toList();

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.totalsByMethod(companyId, today.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            if (row[0] == PaymentMethod.EFECTIVO) cash = (BigDecimal) row[1];
            else card = (BigDecimal) row[1];
        }
        List<TableSession> closed = sessionRepository.findClosedBetween(companyId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal billed = closed.stream().map(TableSession::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DiningDashboardDto(
                (int) tableRepository.countByCompanyId(companyId),
                open.stream().mapToInt(s -> s.getTables().size()).sum(),
                open.size(),
                open.stream().mapToInt(TableSession::getGuests).sum(),
                units(lines, null, ItemStatus.LISTO),
                units(lines, Station.COCINA, ItemStatus.PENDIENTE) + units(lines, Station.COCINA, ItemStatus.PREPARANDO),
                units(lines, Station.BARRA, ItemStatus.PENDIENTE) + units(lines, Station.BARRA, ItemStatus.PREPARANDO),
                orderRepository.countByCompanyIdAndPickupDateAndStatusIn(companyId, today,
                        List.of(OrderStatus.PENDIENTE, OrderStatus.EN_PROCESO, OrderStatus.LISTO)),
                cash.add(card), cash, card, closed.size(),
                closed.isEmpty() ? BigDecimal.ZERO : billed.divide(BigDecimal.valueOf(closed.size()), 2, RoundingMode.HALF_UP));
    }

    /** Unidades con ese estado (y de esa estacion, si se indica). */
    private static int units(List<ServiceLine> lines, Station station, ItemStatus status) {
        return lines.stream()
                .filter(l -> l.getStatus() == status && (station == null || l.getStation() == station))
                .mapToInt(ServiceLine::getQuantity)
                .sum();
    }
}
