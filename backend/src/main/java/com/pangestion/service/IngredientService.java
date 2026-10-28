package com.pangestion.service;

import com.pangestion.dto.IngredientDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Ingredient;
import com.pangestion.repository.IngredientRepository;
import com.pangestion.repository.ProductRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<IngredientDto.Response> findAll() {
        return ingredientRepository.findAllByCompanyIdOrderByNameAsc(currentUser.companyId()).stream().map(this::toResponse).toList();
    }

    /** Extras que se pueden anadir a los productos de una empresa (tambien para la web publica). */
    @Transactional(readOnly = true)
    public List<IngredientDto.Extra> findExtras(Long companyId) {
        return ingredientRepository.findAllByCompanyIdAndExtraPriceIsNotNullOrderByNameAsc(companyId).stream()
                .map(IngredientDto.Extra::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IngredientDto.Response findById(Long id) {
        return toResponse(getEntity(id));
    }

    public IngredientDto.Response create(IngredientDto.Request request) {
        if (ingredientRepository.existsByCompanyIdAndNameIgnoreCase(currentUser.companyId(), request.name())) {
            throw new BusinessException("Ya existe un ingrediente con el nombre " + request.name());
        }
        Ingredient ingredient = new Ingredient();
        ingredient.setCompany(companyService.current());
        apply(ingredient, request);
        return toResponse(ingredientRepository.save(ingredient));
    }

    public IngredientDto.Response update(Long id, IngredientDto.Request request) {
        Ingredient ingredient = getEntity(id);
        if (ingredientRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(currentUser.companyId(), request.name(), id)) {
            throw new BusinessException("Ya existe un ingrediente con el nombre " + request.name());
        }
        apply(ingredient, request);
        return toResponse(ingredient);
    }

    public void delete(Long id) {
        Ingredient ingredient = getEntity(id);
        if (productRepository.countByIngredientsId(id) > 0) {
            throw new BusinessException("No se puede eliminar un ingrediente que llevan algunos productos");
        }
        ingredientRepository.delete(ingredient);
    }

    Ingredient getEntity(Long id) {
        return ingredientRepository.findByIdAndCompanyId(id, currentUser.companyId()).orElseThrow(() -> new NotFoundException("Ingrediente", id));
    }

    private void apply(Ingredient ingredient, IngredientDto.Request request) {
        ingredient.setName(request.name().trim());
        ingredient.setAllergen(request.allergen() == null || request.allergen().isBlank() ? null : request.allergen().trim());
        ingredient.setExtraPrice(request.extraPrice());
    }

    private IngredientDto.Response toResponse(Ingredient ingredient) {
        return IngredientDto.Response.from(ingredient, productRepository.countByIngredientsId(ingredient.getId()));
    }
}
