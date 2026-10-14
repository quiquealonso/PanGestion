package com.pangestion.repository;

import com.pangestion.model.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {

    boolean existsByIngredientId(Long ingredientId);
}
