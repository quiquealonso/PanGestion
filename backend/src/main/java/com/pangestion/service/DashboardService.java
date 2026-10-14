package com.pangestion.service;

import com.pangestion.dto.DashboardDto;
import com.pangestion.dto.IngredientDto;
import com.pangestion.dto.OrderDto;
import com.pangestion.model.OrderStatus;
import com.pangestion.repository.*;
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
    private final IngredientRepository ingredientRepository;

    public DashboardDto getSummary() {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        BigDecimal monthRevenue = orderRepository.sumRevenue(month.atDay(1), month.atEndOfMonth());
        long monthOrders = orderRepository.countBetween(month.atDay(1), month.atEndOfMonth());
        BigDecimal averageTicket = monthOrders == 0
                ? BigDecimal.ZERO
                : monthRevenue.divide(BigDecimal.valueOf(monthOrders), 2, RoundingMode.HALF_UP);

        return new DashboardDto(
                orderRepository.countByStatus(OrderStatus.PENDIENTE),
                orderRepository.countByStatus(OrderStatus.EN_PROCESO),
                orderRepository.countByStatus(OrderStatus.LISTO),
                orderRepository.countByPickupDateAndStatusNot(today, OrderStatus.CANCELADO),
                customerRepository.count(),
                productRepository.countByActiveTrue(),
                monthRevenue,
                monthOrders,
                averageTicket,
                orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 6)).stream()
                        .map(OrderDto.Response::from).toList(),
                orderLineRepository.findTopProducts(PageRequest.of(0, 5)).stream()
                        .map(row -> new DashboardDto.TopProduct((String) row[0], ((Number) row[1]).longValue()))
                        .toList(),
                ingredientRepository.findLowStock().stream().map(IngredientDto.Response::from).toList());
    }
}
