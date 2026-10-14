package com.pangestion.service;

import com.pangestion.dto.CategoryDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Category;
import com.pangestion.repository.CategoryRepository;
import com.pangestion.repository.ProductRepository;
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

    @Transactional(readOnly = true)
    public List<CategoryDto.Response> findAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto.Response findById(Long id) {
        return toResponse(getEntity(id));
    }

    public CategoryDto.Response create(CategoryDto.Request request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("Ya existe una categoria con el nombre " + request.name());
        }
        Category category = new Category();
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    public CategoryDto.Response update(Long id, CategoryDto.Request request) {
        Category category = getEntity(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new BusinessException("Ya existe una categoria con el nombre " + request.name());
        }
        apply(category, request);
        return toResponse(category);
    }

    public void delete(Long id) {
        Category category = getEntity(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new BusinessException("No se puede eliminar una categoria que tiene productos");
        }
        categoryRepository.delete(category);
    }

    Category getEntity(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Categoria", id));
    }

    private void apply(Category category, CategoryDto.Request request) {
        category.setName(request.name().trim());
        category.setColor(request.color().toUpperCase());
        category.setDescription(request.description());
    }

    private CategoryDto.Response toResponse(Category category) {
        return CategoryDto.Response.from(category, productRepository.countByCategoryId(category.getId()));
    }
}
