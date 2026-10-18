package com.pangestion.repository;

import com.pangestion.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findAllByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Ingredient> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}
