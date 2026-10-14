package com.pangestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pangestion.model.Store;
import jakarta.validation.constraints.*;

import java.time.LocalTime;

public final class StoreDto {

    private StoreDto() {
    }

    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String address,
            @Size(max = 20) String phone,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
            @Min(10) @Max(240) int slotMinutes,
            @Min(1) @Max(100) int ordersPerSlot,
            boolean active) {
    }

    public record Response(Long id, String name, String address, String phone,
                           @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
                           int slotMinutes, int ordersPerSlot, boolean active) {

        public static Response from(Store store) {
            return new Response(store.getId(), store.getName(), store.getAddress(), store.getPhone(),
                    store.getOpeningTime(), store.getClosingTime(), store.getSlotMinutes(),
                    store.getOrdersPerSlot(), store.isActive());
        }
    }

    /** Franja de recogida de un dia concreto. */
    public record Slot(@JsonFormat(pattern = "HH:mm") LocalTime time, boolean available, int remaining) {
    }
}
