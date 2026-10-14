package com.pangestion.dto;

import com.pangestion.model.Product;
import com.pangestion.model.ProductIngredient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ProductDto {

    private ProductDto() {
    }

    public record Request(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            boolean active,
            @NotNull Long categoryId,
            @Valid List<IngredientLine> ingredients) {
    }

    public record IngredientLine(@NotNull Long ingredientId, @NotNull @Positive BigDecimal quantity) {
    }

    public record IngredientLineResponse(Long ingredientId, String name, String unit, BigDecimal quantity) {

        static IngredientLineResponse from(ProductIngredient line) {
            return new IngredientLineResponse(line.getIngredient().getId(), line.getIngredient().getName(),
                    line.getIngredient().getUnit(), line.getQuantity());
        }
    }

    /** cost = coste de ingredientes de una unidad (para conocer el margen). */
    public record Response(Long id, String name, String description, BigDecimal price, boolean active,
                           Long categoryId, String categoryName, String categoryColor,
                           BigDecimal cost, List<IngredientLineResponse> ingredients) {

        public static Response from(Product product) {
            BigDecimal cost = product.getIngredients().stream()
                    .map(line -> line.getQuantity().multiply(line.getIngredient().getUnitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            return new Response(product.getId(), product.getName(), product.getDescription(),
                    product.getPrice(), product.isActive(),
                    product.getCategory().getId(), product.getCategory().getName(), product.getCategory().getColor(),
                    cost, product.getIngredients().stream().map(IngredientLineResponse::from).toList());
        }
    }

    /** Version para la parte publica: sin coste ni receta, pero con los alergenos. */
    public record PublicResponse(Long id, String name, String description, BigDecimal price,
                                 Long categoryId, String categoryName, String categoryColor, List<String> allergens) {

        public static PublicResponse from(Product product) {
            List<String> allergens = product.getIngredients().stream()
                    .map(line -> line.getIngredient().getAllergen())
                    .filter(allergen -> allergen != null && !allergen.isBlank() && !allergen.equalsIgnoreCase("Ninguno"))
                    .distinct()
                    .toList();
            return new PublicResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice(),
                    product.getCategory().getId(), product.getCategory().getName(), product.getCategory().getColor(),
                    allergens);
        }
    }
}
