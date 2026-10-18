package com.pangestion.service;

import com.pangestion.dto.DashboardDto;
import com.pangestion.model.OrderStatus;
import com.pangestion.repository.*;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CurrentUser currentUser;

    /** Unidades vendidas cada dia de la semana que empieza el lunes de "anyDay", y de la semana anterior. */
    public DashboardDto.WeeklySales getWeeklySales(LocalDate anyDay) {
        Long companyId = currentUser.companyId();
        LocalDate weekStart = anyDay.with(java.time.DayOfWeek.MONDAY);
        LocalDate previousStart = weekStart.minusWeeks(1);

        java.util.Map<LocalDate, Long> units = new java.util.HashMap<>();
        for (Object[] row : orderLineRepository.unitsPerDay(companyId, previousStart, weekStart.plusDays(6))) {
            units.put((LocalDate) row[0], ((Number) row[1]).longValue());
        }
        java.util.List<Long> current = new java.util.ArrayList<>();
        java.util.List<Long> previous = new java.util.ArrayList<>();
        for (int day = 0; day < 7; day++) {
            current.add(units.getOrDefault(weekStart.plusDays(day), 0L));
            previous.add(units.getOrDefault(previousStart.plusDays(day), 0L));
        }
        return new DashboardDto.WeeklySales(weekStart, current, previous);
    }

    public DashboardDto getSummary() {
        Long companyId = currentUser.companyId();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        BigDecimal monthRevenue = orderRepository.sumRevenue(companyId, month.atDay(1), month.atEndOfMonth());
        long monthOrders = orderRepository.countBetween(companyId, month.atDay(1), month.atEndOfMonth());
        BigDecimal averageTicket = monthOrders == 0
                ? BigDecimal.ZERO
                : monthRevenue.divide(BigDecimal.valueOf(monthOrders), 2, RoundingMode.HALF_UP);

        return new DashboardDto(
                orderRepository.countByCompanyIdAndStatus(companyId, OrderStatus.PENDIENTE),
                orderRepository.countByCompanyIdAndStatus(companyId, OrderStatus.EN_PROCESO),
                orderRepository.countByCompanyIdAndStatus(companyId, OrderStatus.LISTO),
                orderRepository.countByCompanyIdAndPickupDateAndStatusNot(companyId, today, OrderStatus.CANCELADO),
                customerRepository.countByCompanyId(companyId),
                productRepository.countByCompanyIdAndActiveTrue(companyId),
                monthRevenue,
                monthOrders,
                averageTicket,
                orderLineRepository.findTopProducts(companyId, PageRequest.of(0, 5)).stream()
                        .map(row -> new DashboardDto.TopProduct((String) row[0], ((Number) row[1]).longValue()))
                        .toList());
    }
}
