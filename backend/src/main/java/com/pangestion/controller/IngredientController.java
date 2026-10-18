package com.pangestion.controller;

import com.pangestion.dto.IngredientDto;
import com.pangestion.service.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    @GetMapping
    public List<IngredientDto.Response> findAll() {
        return ingredientService.findAll();
    }

    @GetMapping("/{id}")
    public IngredientDto.Response findById(@PathVariable Long id) {
        return ingredientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientDto.Response create(@Valid @RequestBody IngredientDto.Request request) {
        return ingredientService.create(request);
    }

    @PutMapping("/{id}")
    public IngredientDto.Response update(@PathVariable Long id, @Valid @RequestBody IngredientDto.Request request) {
        return ingredientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ingredientService.delete(id);
    }
}
