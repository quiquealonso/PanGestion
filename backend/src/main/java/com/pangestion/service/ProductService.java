package com.pangestion.service;

import com.pangestion.dto.ProductDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Product;
import com.pangestion.model.ProductIngredient;
import com.pangestion.repository.OrderLineRepository;
import com.pangestion.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderLineRepository orderLineRepository;
    private final CategoryService categoryService;
    private final IngredientService ingredientService;

    @Transactional(readOnly = true)
    public List<ProductDto.Response> search(String search, Long categoryId, Boolean active) {
        return productRepository.search(SearchUtils.likePattern(search), categoryId, active).stream()
                .map(ProductDto.Response::from)
                .toList();
    }

    /** Catalogo publico: solo productos activos. */
    @Transactional(readOnly = true)
    public List<ProductDto.PublicResponse> findPublic(Long categoryId) {
        return productRepository.search(SearchUtils.likePattern(null), categoryId, true).stream()
                .map(ProductDto.PublicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDto.Response findById(Long id) {
        return ProductDto.Response.from(getEntity(id));
    }

    public ProductDto.Response create(ProductDto.Request request) {
        Product product = new Product();
        apply(product, request);
        return ProductDto.Response.from(productRepository.save(product));
    }

    public ProductDto.Response update(Long id, ProductDto.Request request) {
        Product product = getEntity(id);
        apply(product, request);
        return ProductDto.Response.from(product);
    }

    /** Activa o desactiva el producto sin borrarlo (decide si se ofrece a los clientes). */
    public ProductDto.Response setActive(Long id, boolean active) {
        Product product = getEntity(id);
        product.setActive(active);
        return ProductDto.Response.from(product);
    }

    public void delete(Long id) {
        Product product = getEntity(id);
        if (orderLineRepository.existsByProductId(id)) {
            throw new BusinessException("El producto aparece en pedidos: desactivalo en lugar de eliminarlo");
        }
        productRepository.delete(product);
    }

    Product getEntity(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Producto", id));
    }

    private void apply(Product product, ProductDto.Request request) {
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setActive(request.active());
        product.setCategory(categoryService.getEntity(request.categoryId()));
        updateRecipe(product, request.ingredients() == null ? List.of() : request.ingredients());
    }

    /**
     * Sincroniza la receta: actualiza cantidades existentes, anade las nuevas y quita las que ya no estan.
     * Se actualiza en sitio para no chocar con la restriccion unica producto-ingrediente.
     */
    private void updateRecipe(Product product, List<ProductDto.IngredientLine> lines) {
        var ids = new HashSet<Long>();
        for (var line : lines) {
            if (!ids.add(line.ingredientId())) {
                throw new BusinessException("Un ingrediente no puede repetirse en la receta");
            }
        }

        Map<Long, ProductIngredient> current = product.getIngredients().stream()
                .collect(Collectors.toMap(pi -> pi.getIngredient().getId(), Function.identity()));

        product.getIngredients().removeIf(pi -> !ids.contains(pi.getIngredient().getId()));

        for (var line : lines) {
            ProductIngredient existing = current.get(line.ingredientId());
            if (existing != null) {
                existing.setQuantity(line.quantity());
            } else {
                product.addIngredient(ingredientService.getEntity(line.ingredientId()), line.quantity());
            }
        }
    }
}
