package com.pangestion.dto;

import com.pangestion.model.Ingredient;
import com.pangestion.model.LineExtra;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class IngredientDto {

    private IngredientDto() {
    }

    /** extraPrice: precio si se anade como extra (null = no se ofrece como extra; 0 = gratis). */
    public record Request(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String allergen,
            @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal extraPrice) {
    }

    /** productCount = en cuantos productos se usa. */
    public record Response(Long id, String name, String allergen, BigDecimal extraPrice, long productCount) {

        public static Response from(Ingredient ingredient, long productCount) {
            return new Response(ingredient.getId(), ingredient.getName(), ingredient.getAllergen(),
                    ingredient.getExtraPrice(), productCount);
        }
    }

    /** Extra que se puede anadir a un producto (para la comanda, los pedidos y la web publica). */
    public record Extra(Long id, String name, String allergen, BigDecimal price) {

        public static Extra from(Ingredient ingredient) {
            return new Extra(ingredient.getId(), ingredient.getName(), ingredient.getAllergen(), ingredient.getExtraPrice());
        }
    }

    /** Extra de una linea ya pedida: nombre y precio del momento. */
    public record LineExtraResponse(String name, BigDecimal price) {

        public static List<LineExtraResponse> from(List<LineExtra> extras) {
            return extras.stream().map(e -> new LineExtraResponse(e.getName(), e.getPrice())).toList();
        }
    }
}
