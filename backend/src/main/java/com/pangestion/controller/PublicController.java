package com.pangestion.controller;

import com.pangestion.dto.CategoryDto;
import com.pangestion.dto.CompanyDto;
import com.pangestion.dto.IngredientDto;
import com.pangestion.dto.OrderDto;
import com.pangestion.dto.ProductDto;
import com.pangestion.service.CategoryService;
import com.pangestion.service.CompanyService;
import com.pangestion.service.IngredientService;
import com.pangestion.service.OrderService;
import com.pangestion.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints sin autenticacion para la web publica: el cliente elige la panaderia (empresa),
 * ve su catalogo y sus franjas, y hace el pedido.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final IngredientService ingredientService;

    /** Panaderias que aceptan pedidos. */
    @GetMapping("/companies")
    public List<CompanyDto.Response> companies() {
        return companyService.findActive();
    }

    @GetMapping("/companies/{id}/slots")
    public List<CompanyDto.Slot> slots(@PathVariable Long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return companyService.getSlots(id, date);
    }

    @GetMapping("/companies/{id}/categories")
    public List<CategoryDto.Response> categories(@PathVariable Long id) {
        return categoryService.findPublic(id);
    }

    /** Solo los productos activos de la panaderia. */
    @GetMapping("/companies/{id}/products")
    public List<ProductDto.PublicResponse> products(@PathVariable Long id,
                                                   @RequestParam(required = false) Long categoryId) {
        return productService.findPublic(id, categoryId);
    }

    /** Extras que se pueden anadir a los productos (con su precio). */
    @GetMapping("/companies/{id}/extras")
    public List<IngredientDto.Extra> extras(@PathVariable Long id) {
        return ingredientService.findExtras(id);
    }

    /** 201 con Location al detalle canonico del pedido (/api/orders/{id}, que consulta el negocio). */
    @PostMapping("/orders")
    public ResponseEntity<OrderDto.Response> createOrder(@Valid @RequestBody OrderDto.PublicRequest request) {
        OrderDto.Response created = orderService.createPublic(request);
        return ResponseEntity.created(Locations.of("/api/orders/{id}", created.id())).body(created);
    }
}
