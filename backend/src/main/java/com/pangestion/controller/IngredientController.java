package com.pangestion.controller;

import com.pangestion.dto.IngredientDto;
import com.pangestion.service.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<IngredientDto.Response> create(@Valid @RequestBody IngredientDto.Request request) {
        IngredientDto.Response created = ingredientService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
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
