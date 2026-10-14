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
    public List<IngredientDto.Response> findAll(@RequestParam(defaultValue = "false") boolean lowStock) {
        return ingredientService.findAll(lowStock);
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

    /** Entrada o salida de stock (reposicion, merma...). */
    @PatchMapping("/{id}/stock")
    public IngredientDto.Response adjustStock(@PathVariable Long id,
                                              @Valid @RequestBody IngredientDto.StockAdjustment request) {
        return ingredientService.adjustStock(id, request.amount());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ingredientService.delete(id);
    }
}
