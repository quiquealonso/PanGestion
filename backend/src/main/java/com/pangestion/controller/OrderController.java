package com.pangestion.controller;

import com.pangestion.dto.OrderDto;
import com.pangestion.model.OrderStatus;
import com.pangestion.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Filtros opcionales: ?search=lola&status=PENDIENTE&from=2026-10-01&to=2026-10-31 */
    @GetMapping
    public List<OrderDto.Response> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderService.search(search, status, from, to);
    }

    @GetMapping("/{id}")
    public OrderDto.Response findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<OrderDto.Response> create(@Valid @RequestBody OrderDto.Request request) {
        OrderDto.Response created = orderService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/{id}")
    public OrderDto.Response update(@PathVariable Long id, @Valid @RequestBody OrderDto.Request request) {
        return orderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public OrderDto.Response changeStatus(@PathVariable Long id, @Valid @RequestBody OrderDto.StatusRequest request) {
        return orderService.changeStatus(id, request.status());
    }

    /**
     * Hoja de produccion de un dia: unidades por producto y hora de recogida.
     * pending=true (por defecto) deja fuera los pedidos ya entregados.
     */
    @GetMapping("/production")
    public List<OrderDto.ProductionRow> production(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "true") boolean pending) {
        return orderService.production(date, pending);
    }

    /** Pasa a EN_PROCESO todos los pedidos pendientes que se recogen ese dia. Devuelve {"updated": n}. */
    @PostMapping("/start-day")
    public java.util.Map<String, Integer> startDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return java.util.Map.of("updated", orderService.startDay(date));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }
}
