package com.pangestion.controller;

import com.pangestion.dto.ProductDto;
import com.pangestion.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** Filtros opcionales: ?search=pan&categoryId=1&active=true */
    @GetMapping
    public List<ProductDto.Response> search(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Boolean active) {
        return productService.search(search, categoryId, active);
    }

    @GetMapping("/{id}")
    public ProductDto.Response findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.Response create(@Valid @RequestBody ProductDto.Request request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductDto.Response update(@PathVariable Long id, @Valid @RequestBody ProductDto.Request request) {
        return productService.update(id, request);
    }

    /** Body: {"active": true|false} */
    @PatchMapping("/{id}/active")
    public ProductDto.Response setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return productService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
