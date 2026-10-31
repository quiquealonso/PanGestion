package com.pangestion.controller;

import com.pangestion.dto.CategoryDto;
import com.pangestion.dto.CompanyDto;
import com.pangestion.dto.IngredientDto;
import com.pangestion.dto.OrderDto;
import com.pangestion.dto.ProductDto;
import com.pangestion.security.RateLimiter;
import com.pangestion.service.CategoryService;
import com.pangestion.service.CustomerAccountService;
import com.pangestion.service.CompanyService;
import com.pangestion.service.IngredientService;
import com.pangestion.service.OrderService;
import com.pangestion.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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
    private final RateLimiter rateLimiter;
    private final CustomerAccountService accountService;

    /** Panaderias que aceptan pedidos. */
    @GetMapping("/companies")
    public List<CompanyDto.PublicResponse> companies() {
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

    /**
     * 201 con Location al detalle canonico del pedido (/api/orders/{id}, que consulta el negocio).
     * Solo con cuenta de cliente (token con rol CLIENTE, ver SecurityConfig).
     * Contra bots: como mucho 10 pedidos por hora desde una IP, y el campo trampa "website" debe llegar vacio.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderDto.PublicResponse> createOrder(@Valid @RequestBody OrderDto.PublicRequest request,
                                                               @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        rateLimiter.check("public-order-ip:" + http.getRemoteAddr(), 10, Duration.ofHours(1));
        OrderDto.PublicResponse created = orderService.createPublic(request, accountService.getVerified(jwt.getSubject()));
        return ResponseEntity.created(Locations.of("/api/orders/{id}", created.id())).body(created);
    }
}
