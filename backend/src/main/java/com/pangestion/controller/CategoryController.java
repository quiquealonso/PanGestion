package com.pangestion.controller;

import com.pangestion.dto.CategoryDto;
import com.pangestion.dto.SortRequest;
import com.pangestion.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryDto.Response> findAll() {
        return categoryService.findAll();
    }

    @GetMapping("/{id}")
    public CategoryDto.Response findById(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<CategoryDto.Response> create(@Valid @RequestBody CategoryDto.Request request) {
        CategoryDto.Response created = categoryService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CategoryDto.Response update(@PathVariable Long id, @Valid @RequestBody CategoryDto.Request request) {
        return categoryService.update(id, request);
    }

    /** Body: {"ids": [3, 1, 2]} con las categorias en el orden deseado. */
    @PutMapping("/order")
    public List<CategoryDto.Response> reorder(@Valid @RequestBody SortRequest request) {
        return categoryService.reorder(request.ids());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
