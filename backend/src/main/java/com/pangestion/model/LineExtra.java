package com.pangestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Extra anadido a un producto pedido ("pizza con huevo", +1,00 €). Se guarda el nombre y el precio del momento,
 * como el precio del producto: el pedido no cambia aunque luego se edite o borre el ingrediente.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LineExtra {

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Suma de los precios de unos extras (lo que se anade al precio del producto). */
    public static BigDecimal total(List<LineExtra> extras) {
        return extras.stream().map(LineExtra::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Nombres de los extras, para comparar dos lineas (la coleccion de Hibernate no compara contenido). */
    public static List<String> names(List<LineExtra> extras) {
        return extras.stream().map(LineExtra::getName).toList();
    }
}
