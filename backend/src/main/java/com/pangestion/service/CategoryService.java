package com.pangestion.service;

import com.pangestion.dto.CategoryDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Category;
import com.pangestion.repository.CategoryRepository;
import com.pangestion.repository.ProductRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<CategoryDto.Response> findAll() {
        return categoryRepository.findAllByCompanyIdOrderBySortOrderAscNameAsc(currentUser.companyId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Categorias de una empresa para la web publica. */
    @Transactional(readOnly = true)
    public List<CategoryDto.Response> findPublic(Long companyId) {
        return categoryRepository.findAllByCompanyIdOrderBySortOrderAscNameAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto.Response findById(Long id) {
        return toResponse(getEntity(id));
    }

    public CategoryDto.Response create(CategoryDto.Request request) {
        if (categoryRepository.existsByCompanyIdAndNameIgnoreCase(currentUser.companyId(), request.name())) {
            throw new BusinessException("Ya existe una categoria con el nombre " + request.name());
        }
        Category category = new Category();
        category.setCompany(companyService.current());
        apply(category, request);
        category.setSortOrder(categoryRepository.nextSortOrder(currentUser.companyId()));
        category.setKitchenOrder(category.getSortOrder());
        return toResponse(categoryRepository.save(category));
    }

    public CategoryDto.Response update(Long id, CategoryDto.Request request) {
        Category category = getEntity(id);
        if (categoryRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(currentUser.companyId(), request.name(), id)) {
            throw new BusinessException("Ya existe una categoria con el nombre " + request.name());
        }
        apply(category, request);
        return toResponse(category);
    }

    /** Guarda el nuevo orden: cada categoria toma la posicion que ocupa en la lista. */
    public List<CategoryDto.Response> reorder(List<Long> ids) {
        for (int position = 0; position < ids.size(); position++) {
            getEntity(ids.get(position)).setSortOrder(position);
        }
        return findAll();
    }

    public void delete(Long id) {
        Category category = getEntity(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new BusinessException("No se puede eliminar una categoria que tiene productos");
        }
        categoryRepository.delete(category);
    }

    Category getEntity(Long id) {
        return categoryRepository.findByIdAndCompanyId(id, currentUser.companyId()).orElseThrow(() -> new NotFoundException("Categoria", id));
    }

    /**
     * Orden de las categorias en la pantalla de cocina: cada una toma la posicion que ocupa en la lista.
     * No cambia el orden del catalogo ni de la web.
     */
    public List<CategoryDto.Response> reorderKitchen(List<Long> ids) {
        for (int position = 0; position < ids.size(); position++) {
            getEntity(ids.get(position)).setKitchenOrder(position);
        }
        return findAll();
    }

    private void apply(Category category, CategoryDto.Request request) {
        category.setName(request.name().trim());
        category.setColor(request.color().toUpperCase());
        category.setDescription(request.description());
        category.setStation(request.station());
        // Si la categoria dice donde se prepara, todos sus productos pasan ahi (Bebidas → barra)
        if (request.station() != null && category.getId() != null) {
            productRepository.findAllByCategoryId(category.getId()).forEach(p -> p.setStation(request.station()));
        }
    }

    private CategoryDto.Response toResponse(Category category) {
        return CategoryDto.Response.from(category, productRepository.countByCategoryId(category.getId()));
    }
}
