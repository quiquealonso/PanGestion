package com.pangestion.controller;

import com.pangestion.dto.ProductDto;
import com.pangestion.dto.SortRequest;
import com.pangestion.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<ProductDto.Response> create(@Valid @RequestBody ProductDto.Request request) {
        ProductDto.Response created = productService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductDto.Response update(@PathVariable Long id, @Valid @RequestBody ProductDto.Request request) {
        return productService.update(id, request);
    }

    /** Body: {"active": true|false} */
    @PatchMapping("/{id}/active")
    public ProductDto.Response setActive(@PathVariable Long id, @Valid @RequestBody ProductDto.ActiveRequest body) {
        return productService.setActive(id, body.active());
    }

    /** Body: {"ids": [7, 5, 6]} con los productos de una categoria en el orden deseado. */
    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody SortRequest request) {
        productService.reorder(request.ids());
    }

    /** Sube o sustituye la imagen (multipart, campo "file"). JPG, PNG o WEBP de hasta 2 MB. */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDto.Response uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return productService.uploadImage(id, file);
    }

    @DeleteMapping("/{id}/image")
    public ProductDto.Response removeImage(@PathVariable Long id) {
        return productService.removeImage(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
