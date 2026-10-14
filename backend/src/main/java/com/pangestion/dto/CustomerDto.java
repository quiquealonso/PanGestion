package com.pangestion.dto;

import com.pangestion.model.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class CustomerDto {

    private CustomerDto() {
    }

    /** companyId nulo = cliente particular. */
    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @Size(max = 20) String phone,
            Long companyId) {
    }

    public record Response(Long id, String name, String email, String phone,
                           Long companyId, String companyName, LocalDateTime createdAt) {

        public static Response from(Customer customer) {
            var company = customer.getCompany();
            return new Response(customer.getId(), customer.getName(), customer.getEmail(), customer.getPhone(),
                    company == null ? null : company.getId(),
                    company == null ? null : company.getName(),
                    customer.getCreatedAt());
        }
    }
}
