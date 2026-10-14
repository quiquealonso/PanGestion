package com.pangestion.dto;

import com.pangestion.model.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoryDto {

    private CategoryDto() {
    }

    public record Request(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "debe ser un color hexadecimal, por ejemplo #F59E0B") String color,
            @Size(max = 255) String description) {
    }

    public record Response(Long id, String name, String color, String description, long productCount) {

        public static Response from(Category category, long productCount) {
            return new Response(category.getId(), category.getName(), category.getColor(),
                    category.getDescription(), productCount);
        }
    }
}
