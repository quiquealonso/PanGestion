package com.pangestion.service;

import com.pangestion.dto.ProductDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Ingredient;
import com.pangestion.model.LineExtra;
import com.pangestion.model.Product;
import com.pangestion.repository.IngredientRepository;
import com.pangestion.repository.OrderLineRepository;
import com.pangestion.repository.ProductRepository;
import com.pangestion.security.CurrentUser;
import com.pangestion.storage.ImageStorage;
import com.pangestion.storage.ImageType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderLineRepository orderLineRepository;
    private final CategoryService categoryService;
    private final IngredientService ingredientService;
    private final CompanyService companyService;
    private final CurrentUser currentUser;
    private final ImageStorage imageStorage;
    private final IngredientRepository ingredientRepository;

    @Value("${app.uploads.max-bytes}")
    private long maxImageBytes;

    @Transactional(readOnly = true)
    public List<ProductDto.Response> search(String search, Long categoryId, Boolean active) {
        return productRepository.search(currentUser.companyId(), SearchUtils.likePattern(search), categoryId, active).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Catalogo publico: solo productos activos. */
    @Transactional(readOnly = true)
    public List<ProductDto.PublicResponse> findPublic(Long companyId, Long categoryId) {
        return productRepository.search(companyId, SearchUtils.likePattern(null), categoryId, true).stream()
                .map(product -> ProductDto.PublicResponse.from(product, imageStorage.url(product.getImagePath())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDto.Response findById(Long id) {
        return toResponse(getEntity(id));
    }

    public ProductDto.Response create(ProductDto.Request request) {
        Product product = new Product();
        product.setCompany(companyService.current());
        apply(product, request);
        product.setSortOrder(productRepository.nextSortOrder(request.categoryId()));
        return toResponse(productRepository.save(product));
    }

    public ProductDto.Response update(Long id, ProductDto.Request request) {
        Product product = getEntity(id);
        boolean categoryChanged = !product.getCategory().getId().equals(request.categoryId());
        if (categoryChanged) {
            // Al cambiar de categoria pasa al final de la nueva
            product.setSortOrder(productRepository.nextSortOrder(request.categoryId()));
        }
        apply(product, request);
        return toResponse(product);
    }

    /** Nuevo orden de los productos de una categoria: cada uno toma la posicion que ocupa en la lista. */
    public void reorder(List<Long> ids) {
        List<Product> products = ids.stream().map(this::getEntity).toList();
        long categories = products.stream().map(p -> p.getCategory().getId()).distinct().count();
        if (categories > 1) {
            throw new BusinessException("Solo se pueden ordenar productos de una misma categoria");
        }
        for (int position = 0; position < products.size(); position++) {
            products.get(position).setSortOrder(position);
        }
    }

    /** Activa o desactiva el producto sin borrarlo (decide si se ofrece a los clientes). */
    public ProductDto.Response setActive(Long id, boolean active) {
        Product product = getEntity(id);
        product.setActive(active);
        return toResponse(product);
    }

    public void delete(Long id) {
        Product product = getEntity(id);
        if (orderLineRepository.existsByProductId(id)) {
            throw new BusinessException("El producto aparece en pedidos: desactivalo en lugar de eliminarlo");
        }
        productRepository.delete(product);
        imageStorage.delete(product.getImagePath());
    }

    /**
     * Sube o sustituye la imagen del producto. Se valida por contenido (JPEG, PNG o WEBP) y por tamano.
     * Se guarda en la carpeta de la empresa: {empresa}/products/{nombre aleatorio}.
     */
    public ProductDto.Response uploadImage(Long id, MultipartFile file) {
        Product product = getEntity(id);
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer la imagen");
        }
        if (data.length == 0 || data.length > maxImageBytes) {
            throw new BusinessException("La imagen debe ocupar como maximo " + (maxImageBytes / 1024 / 1024) + " MB");
        }
        ImageType type = ImageType.detect(data);
        if (type == null) {
            throw new BusinessException("Formato no valido: sube una imagen JPG, PNG o WEBP");
        }
        String previous = product.getImagePath();
        product.setImagePath(imageStorage.save(product.getCompany().getId() + "/products", data, type));
        imageStorage.delete(previous);
        return toResponse(product);
    }

    public ProductDto.Response removeImage(Long id) {
        Product product = getEntity(id);
        imageStorage.delete(product.getImagePath());
        product.setImagePath(null);
        return toResponse(product);
    }

    private ProductDto.Response toResponse(Product product) {
        return ProductDto.Response.from(product, imageStorage.url(product.getImagePath()));
    }

    /** Para pedidos publicos: producto de una empresa concreta (sin usuario logueado). */
    Product getEntity(Long id, Long companyId) {
        return productRepository.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new NotFoundException("Producto", id));
    }

    Product getEntity(Long id) {
        return productRepository.findByIdAndCompanyId(id, currentUser.companyId()).orElseThrow(() -> new NotFoundException("Producto", id));
    }

    private void apply(Product product, ProductDto.Request request) {
        product.setName(request.name().trim());
        product.setAbbreviation(blankToNull(request.abbreviation()));
        product.setDescription(request.description());
        product.setColor(request.color() == null || request.color().isBlank() ? null : request.color().toUpperCase());
        product.setPrice(request.price());
        product.setActive(request.active());
        product.setCategory(categoryService.getEntity(request.categoryId()));
        if (product.getCategory().getStation() != null) {
            // La categoria manda (Bebidas → barra)
            product.setStation(product.getCategory().getStation());
        } else if (request.station() != null) {
            product.setStation(request.station());
        }
        setIngredients(product, request.ingredientIds() == null ? List.of() : request.ingredientIds());
    }

    /**
     * Extras a anadir a un producto (nombre y precio del momento). Tienen que ser ingredientes de la misma empresa
     * con precio de extra, y el producto tiene que llevar ingredientes (una bebida no admite extras).
     * Tambien lo usan los pedidos para llevar y la web publica.
     */
    List<LineExtra> extras(Product product, Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return List.of();
        }
        if (product.getIngredients().isEmpty()) {
            throw new BusinessException("El producto " + product.getName() + " no admite extras");
        }
        List<LineExtra> extras = new ArrayList<>();
        for (Long id : new TreeSet<>(ingredientIds)) {
            Ingredient ingredient = ingredientRepository.findByIdAndCompanyId(id, product.getCompany().getId())
                    .orElseThrow(() -> new NotFoundException("Ingrediente", id));
            if (ingredient.getExtraPrice() == null) {
                throw new BusinessException(ingredient.getName() + " no se puede anadir como extra");
            }
            extras.add(new LineExtra(ingredient.getName(), ingredient.getExtraPrice()));
        }
        extras.sort(Comparator.comparing(LineExtra::getName, String.CASE_INSENSITIVE_ORDER));
        return extras;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Sustituye los ingredientes del producto por los indicados (sin repetidos). */
    private void setIngredients(Product product, List<Long> ingredientIds) {
        product.getIngredients().clear();
        new LinkedHashSet<>(ingredientIds)
                .forEach(id -> product.getIngredients().add(ingredientService.getEntity(id)));
    }
}
