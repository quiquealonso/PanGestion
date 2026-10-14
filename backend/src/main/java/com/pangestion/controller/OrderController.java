package com.pangestion.controller;

import com.pangestion.dto.OrderDto;
import com.pangestion.model.OrderStatus;
import com.pangestion.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Filtros opcionales: ?search=lola&status=PENDIENTE&storeId=1&from=2026-10-01&to=2026-10-31 */
    @GetMapping
    public List<OrderDto.Response> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderService.search(search, status, storeId, from, to);
    }

    @GetMapping("/{id}")
    public OrderDto.Response findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto.Response create(@Valid @RequestBody OrderDto.Request request) {
        return orderService.create(request);
    }

    @PutMapping("/{id}")
    public OrderDto.Response update(@PathVariable Long id, @Valid @RequestBody OrderDto.Request request) {
        return orderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public OrderDto.Response changeStatus(@PathVariable Long id, @Valid @RequestBody OrderDto.StatusRequest request) {
        return orderService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }
}
