package com.pangestion.controller;

import com.pangestion.dto.CategoryDto;
import com.pangestion.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto.Response create(@Valid @RequestBody CategoryDto.Request request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    public CategoryDto.Response update(@PathVariable Long id, @Valid @RequestBody CategoryDto.Request request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
