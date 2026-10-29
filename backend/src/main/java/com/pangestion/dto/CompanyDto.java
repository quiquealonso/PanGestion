package com.pangestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.storage.ImageStorage;
import com.pangestion.validation.ValidOpeningHours;
import jakarta.validation.constraints.*;

import java.time.LocalTime;

public final class CompanyDto {

    private CompanyDto() {
    }

    /** @ValidOpeningHours: la apertura antes que el cierre (si no, 400 en closingTime). */
    @ValidOpeningHours
    public record Request(
            @NotBlank @Size(max = 120) String name,
            /** Solo lo cambia el SUPERADMIN; desde "Mi empresa" se ignora. */
            @NotNull BusinessType type,
            @NotBlank @Size(max = 20) String taxId,
            @Email @Size(max = 120) String email,
            @Size(max = 20) String phone,
            @NotBlank @Size(max = 200) String address,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
            @Min(10) @Max(240) int slotMinutes,
            @Min(1) @Max(100) int ordersPerSlot,
            boolean active,
            /** Hace envios a domicilio; null = no cambiarlo (o no, al crear). */
            Boolean delivery) {
    }

    public record Response(Long id, String name, BusinessType type, String taxId, String email, String phone, String address,
                           @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
                           int slotMinutes, int ordersPerSlot, boolean active, boolean delivery,
                           long userCount, long customerCount,
                           /** URL del logo (/uploads/...); null = sin logo */
                           String logoUrl) {

        public static Response from(Company company, long userCount, long customerCount) {
            return new Response(company.getId(), company.getName(), company.getType(), company.getTaxId(), company.getEmail(),
                    company.getPhone(), company.getAddress(), company.getOpeningTime(), company.getClosingTime(),
                    company.getSlotMinutes(), company.getOrdersPerSlot(), company.isActive(), company.isDelivery(),
                    userCount, customerCount, ImageStorage.uploadsUrl(company.getLogoPath()));
        }
    }

    /** Franja de recogida de un dia. */
    public record Slot(@JsonFormat(pattern = "HH:mm") LocalTime time, boolean available, int remaining) {
    }
}
