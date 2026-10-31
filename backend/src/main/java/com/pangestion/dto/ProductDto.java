package com.pangestion.dto;

import com.pangestion.model.Ingredient;
import com.pangestion.model.Product;
import com.pangestion.model.Station;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class ProductDto {

    private ProductDto() {
    }

    /** Activar o desactivar un producto: {"active": true|false} */
    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record Request(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String abbreviation,
            @Size(max = 500) String description,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "debe ser un color hexadecimal, por ejemplo #F59E0B") String color,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            boolean active,
            @NotNull Long categoryId,
            /** Ingredientes que lleva (ids). */
            List<Long> ingredientIds,
            /** Donde se prepara; null = cocina (o no cambiarla al editar). */
            Station station) {
    }

    public record IngredientRef(Long id, String name, String allergen) {

        static IngredientRef from(Ingredient ingredient) {
            return new IngredientRef(ingredient.getId(), ingredient.getName(), ingredient.getAllergen());
        }
    }

    /** color = color propio (puede ser nulo); displayColor = el que se pinta (propio o el de la categoria). */
    public record Response(Long id, String name, String abbreviation, String description, BigDecimal price,
                           boolean active, String color, String displayColor, int sortOrder,
                           Long categoryId, String categoryName, String categoryColor,
                           String imageUrl, List<IngredientRef> ingredients, Station station) {

        /** imageUrl la calcula el servicio con ImageStorage (depende de donde se guarden las imagenes). */
        public static Response from(Product product, String imageUrl) {
            return new Response(product.getId(), product.getName(), product.getAbbreviation(),
                    product.getDescription(), product.getPrice(), product.isActive(),
                    product.getColor(), product.getDisplayColor(), product.getSortOrder(),
                    product.getCategory().getId(), product.getCategory().getName(), product.getCategory().getColor(),
                    imageUrl, product.getIngredients().stream().map(IngredientRef::from).toList(), product.getStation());
        }
    }

    /**
     * Version para la parte publica: con los ingredientes (para informar y para poder quitarlos, "sin cebolla")
     * y los alergenos que resultan de ellos.
     */
    public record PublicResponse(Long id, String name, String description, BigDecimal price, String color, String imageUrl,
                                 Long categoryId, String categoryName, String categoryColor,
                                 List<IngredientRef> ingredients, List<String> allergens) {

        public static PublicResponse from(Product product, String imageUrl) {
            List<String> allergens = product.getIngredients().stream()
                    .map(Ingredient::getAllergen)
                    .filter(allergen -> allergen != null && !allergen.isBlank())
                    .distinct()
                    .toList();
            return new PublicResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice(),
                    product.getDisplayColor(), imageUrl,
                    product.getCategory().getId(), product.getCategory().getName(), product.getCategory().getColor(),
                    product.getIngredients().stream().map(IngredientRef::from).toList(), allergens);
        }
    }
}
