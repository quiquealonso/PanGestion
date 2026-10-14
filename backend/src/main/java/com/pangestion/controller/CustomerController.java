package com.pangestion.controller;

import com.pangestion.dto.CustomerDto;
import com.pangestion.dto.OrderDto;
import com.pangestion.service.CustomerService;
import com.pangestion.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDto.Response create(@Valid @RequestBody CustomerDto.Request request) {
        return customerService.create(request);
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
