package com.pangestion.dto;

import com.pangestion.model.Category;
import com.pangestion.model.Station;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoryDto {

    private CategoryDto() {
    }

    public record Request(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "debe ser un color hexadecimal, por ejemplo #F59E0B") String color,
            @Size(max = 255) String description,
            /** Donde se preparan sus productos (bares y restaurantes); null = lo decide cada producto. */
            Station station) {
    }

    public record Response(Long id, String name, String color, String description, int sortOrder, long productCount,
                           Station station, int kitchenOrder) {

        public static Response from(Category category, long productCount) {
            return new Response(category.getId(), category.getName(), category.getColor(),
                    category.getDescription(), category.getSortOrder(), productCount,
                    category.getStation(), category.getKitchenOrder());
        }
    }
}
