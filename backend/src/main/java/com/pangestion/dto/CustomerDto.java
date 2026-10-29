package com.pangestion.dto;

import com.pangestion.model.Customer;
import com.pangestion.repository.OrderRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CustomerDto {

    private CustomerDto() {
    }

    /** businessName/taxId solo si el cliente es un negocio; nulos = particular. */
    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @Size(max = 20) String phone,
            @Size(max = 120) String businessName,
            @Size(max = 20) String taxId) {
    }

    /** orderCount, totalSpent y lastOrderDate resumen sus pedidos no cancelados (lastOrderDate nulo si no tiene). */
    public record Response(Long id, String name, String email, String phone,
                           String businessName, String taxId, LocalDateTime createdAt,
                           long orderCount, BigDecimal totalSpent, LocalDate lastOrderDate) {

        public static Response from(Customer customer, OrderRepository.CustomerOrderStats stats) {
            return new Response(customer.getId(), customer.getName(), customer.getEmail(), customer.getPhone(),
                    customer.getBusinessName(), customer.getTaxId(), customer.getCreatedAt(),
                    stats == null ? 0 : stats.getOrderCount(),
                    stats == null ? BigDecimal.ZERO : stats.getTotalSpent(),
                    stats == null ? null : stats.getLastOrderDate());
        }
    }
}
