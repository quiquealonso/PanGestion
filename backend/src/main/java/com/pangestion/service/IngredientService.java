package com.pangestion.service;

import com.pangestion.dto.IngredientDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Ingredient;
import com.pangestion.repository.IngredientRepository;
import com.pangestion.repository.ProductIngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final ProductIngredientRepository productIngredientRepository;

    @Transactional(readOnly = true)
    public List<IngredientDto.Response> findAll(boolean lowStockOnly) {
        List<Ingredient> ingredients = lowStockOnly
                ? ingredientRepository.findLowStock()
                : ingredientRepository.findAllByOrderByNameAsc();
        return ingredients.stream().map(IngredientDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public IngredientDto.Response findById(Long id) {
        return IngredientDto.Response.from(getEntity(id));
    }

    public IngredientDto.Response create(IngredientDto.Request request) {
        if (ingredientRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("Ya existe un ingrediente con el nombre " + request.name());
        }
        Ingredient ingredient = new Ingredient();
        apply(ingredient, request);
        return IngredientDto.Response.from(ingredientRepository.save(ingredient));
    }

    public IngredientDto.Response update(Long id, IngredientDto.Request request) {
        Ingredient ingredient = getEntity(id);
        if (ingredientRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BusinessException("Ya existe un ingrediente con el nombre " + request.name());
        }
        apply(ingredient, request);
        return IngredientDto.Response.from(ingredient);
    }

    /** Entrada (cantidad positiva) o salida (negativa) de stock. */
    public IngredientDto.Response adjustStock(Long id, BigDecimal amount) {
        Ingredient ingredient = getEntity(id);
        BigDecimal newStock = ingredient.getStock().add(amount);
        if (newStock.signum() < 0) {
            throw new BusinessException("El stock de " + ingredient.getName() + " no puede quedar en negativo");
        }
        ingredient.setStock(newStock);
        return IngredientDto.Response.from(ingredient);
    }

    public void delete(Long id) {
        Ingredient ingredient = getEntity(id);
        if (productIngredientRepository.existsByIngredientId(id)) {
            throw new BusinessException("No se puede eliminar un ingrediente que usan algunos productos");
        }
        ingredientRepository.delete(ingredient);
    }

    Ingredient getEntity(Long id) {
        return ingredientRepository.findById(id).orElseThrow(() -> new NotFoundException("Ingrediente", id));
    }

    private void apply(Ingredient ingredient, IngredientDto.Request request) {
        ingredient.setName(request.name().trim());
        ingredient.setUnit(request.unit().trim());
        ingredient.setStock(request.stock());
        ingredient.setMinStock(request.minStock());
        ingredient.setUnitCost(request.unitCost());
        ingredient.setAllergen(request.allergen());
    }
}
