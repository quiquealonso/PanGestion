package com.pangestion.repository;

import com.pangestion.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findAllByOrderByNameAsc();

    @Query("select i from Ingredient i where i.stock <= i.minStock order by i.stock asc")
    List<Ingredient> findLowStock();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
