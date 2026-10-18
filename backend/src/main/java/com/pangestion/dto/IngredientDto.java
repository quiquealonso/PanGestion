package com.pangestion.dto;

import com.pangestion.model.Ingredient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class IngredientDto {

    private IngredientDto() {
    }

    public record Request(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String allergen) {
    }

    /** productCount = en cuantos productos se usa. */
    public record Response(Long id, String name, String allergen, long productCount) {

        public static Response from(Ingredient ingredient, long productCount) {
            return new Response(ingredient.getId(), ingredient.getName(), ingredient.getAllergen(), productCount);
        }
    }
}
