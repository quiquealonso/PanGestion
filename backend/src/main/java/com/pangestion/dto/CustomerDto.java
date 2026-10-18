package com.pangestion.dto;

import com.pangestion.model.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

    public record Response(Long id, String name, String email, String phone,
                           String businessName, String taxId, LocalDateTime createdAt) {

        public static Response from(Customer customer) {
            return new Response(customer.getId(), customer.getName(), customer.getEmail(), customer.getPhone(),
                    customer.getBusinessName(), customer.getTaxId(), customer.getCreatedAt());
        }
    }
}
