package com.pangestion.controller;

import com.pangestion.dto.CategoryDto;
import com.pangestion.dto.OrderDto;
import com.pangestion.dto.ProductDto;
import com.pangestion.dto.StoreDto;
import com.pangestion.service.CategoryService;
import com.pangestion.service.OrderService;
import com.pangestion.service.ProductService;
import com.pangestion.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Endpoints sin autenticacion para la pantalla publica de Nuevo pedido. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final StoreService storeService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;

    @GetMapping("/stores")
    public List<StoreDto.Response> stores() {
        return storeService.findAll(true);
    }

    @GetMapping("/stores/{id}/slots")
    public List<StoreDto.Slot> slots(@PathVariable Long id,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return storeService.getSlots(id, date);
    }

    @GetMapping("/categories")
    public List<CategoryDto.Response> categories() {
        return categoryService.findAll();
    }

    /** Solo los productos activos. */
    @GetMapping("/products")
    public List<ProductDto.PublicResponse> products(@RequestParam(required = false) Long categoryId) {
        return productService.findPublic(categoryId);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto.Response createOrder(@Valid @RequestBody OrderDto.PublicRequest request) {
        return orderService.createPublic(request);
    }
}
