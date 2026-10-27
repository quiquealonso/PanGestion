package com.pangestion.controller;

import com.pangestion.dto.CustomerDto;
import com.pangestion.dto.OrderDto;
import com.pangestion.service.CustomerService;
import com.pangestion.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final OrderService orderService;

    /** ?search= busca por nombre, email o empresa. */
    @GetMapping
    public List<CustomerDto.Response> search(@RequestParam(required = false) String search) {
        return customerService.search(search);
    }

    @GetMapping("/{id}")
    public CustomerDto.Response findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    /** Historial de pedidos del cliente. */
    @GetMapping("/{id}/orders")
    public List<OrderDto.Response> findOrders(@PathVariable Long id) {
        return orderService.findByCustomer(id);
    }

    /**
     * Creacion anidada (S9): pedido para este cliente. La ruta decide el cliente (el cuerpo no lleva customerId)
     * y Location apunta al detalle canonico /api/orders/{id}.
     */
    @PostMapping("/{id}/orders")
    public ResponseEntity<OrderDto.Response> createOrder(@PathVariable Long id,
                                                         @Valid @RequestBody OrderDto.CustomerOrderRequest request) {
        OrderDto.Response created = orderService.createForCustomer(id, request);
        return ResponseEntity.created(Locations.of("/api/orders/{id}", created.id())).body(created);
    }

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<CustomerDto.Response> create(@Valid @RequestBody CustomerDto.Request request) {
        CustomerDto.Response created = customerService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CustomerDto.Response update(@PathVariable Long id, @Valid @RequestBody CustomerDto.Request request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
