package com.pangestion.dto;

import com.pangestion.model.Ingredient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class IngredientDto {

    private IngredientDto() {
    }

    public record Request(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 10) String unit,
            @NotNull @PositiveOrZero BigDecimal stock,
            @NotNull @PositiveOrZero BigDecimal minStock,
            @NotNull @PositiveOrZero BigDecimal unitCost,
            @Size(max = 100) String allergen) {
    }

    /** Ajuste de stock: cantidad positiva para entradas, negativa para salidas. */
    public record StockAdjustment(@NotNull BigDecimal amount) {
    }

    public record Response(Long id, String name, String unit, BigDecimal stock, BigDecimal minStock,
                           BigDecimal unitCost, String allergen, boolean lowStock) {

        public static Response from(Ingredient ingredient) {
            return new Response(ingredient.getId(), ingredient.getName(), ingredient.getUnit(),
                    ingredient.getStock(), ingredient.getMinStock(), ingredient.getUnitCost(),
                    ingredient.getAllergen(), ingredient.isLowStock());
        }
    }
}
