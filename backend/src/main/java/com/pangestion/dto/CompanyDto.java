package com.pangestion.dto;

import com.pangestion.model.Company;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CompanyDto {

    private CompanyDto() {
    }

    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 20) String taxId,
            @Email @Size(max = 120) String email,
            @Size(max = 20) String phone,
            @Size(max = 200) String address) {
    }

    public record Response(Long id, String name, String taxId, String email, String phone, String address,
                           long customerCount) {

        public static Response from(Company company, long customerCount) {
            return new Response(company.getId(), company.getName(), company.getTaxId(), company.getEmail(),
                    company.getPhone(), company.getAddress(), customerCount);
        }
    }
}
