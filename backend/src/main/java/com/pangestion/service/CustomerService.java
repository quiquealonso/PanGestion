package com.pangestion.service;

import com.pangestion.dto.CustomerDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Customer;
import com.pangestion.repository.CustomerRepository;
import com.pangestion.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final CompanyService companyService;

    @Transactional(readOnly = true)
    public List<CustomerDto.Response> search(String search) {
        return customerRepository.search(SearchUtils.likePattern(search)).stream()
                .map(CustomerDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerDto.Response findById(Long id) {
        return CustomerDto.Response.from(getEntity(id));
    }

    public CustomerDto.Response create(CustomerDto.Request request) {
        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Ya existe un cliente con el email " + request.email());
        }
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerDto.Response.from(customerRepository.save(customer));
    }

    public CustomerDto.Response update(Long id, CustomerDto.Request request) {
        Customer customer = getEntity(id);
        if (customerRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new BusinessException("Ya existe un cliente con el email " + request.email());
        }
        apply(customer, request);
        return CustomerDto.Response.from(customer);
    }

    public void delete(Long id) {
        Customer customer = getEntity(id);
        if (orderRepository.existsByCustomerId(id)) {
            throw new BusinessException("No se puede eliminar un cliente con pedidos");
        }
        customerRepository.delete(customer);
    }

    /**
     * Para la parte publica: busca el cliente por email y actualiza su nombre y telefono,
     * o lo crea como particular si no existe.
     */
    Customer findOrCreate(CustomerDto.Request request) {
        return customerRepository.findByEmailIgnoreCase(request.email().trim())
                .map(existing -> {
                    existing.setName(request.name().trim());
                    existing.setPhone(request.phone());
                    return existing;
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setName(request.name().trim());
                    customer.setEmail(request.email().trim().toLowerCase());
                    customer.setPhone(request.phone());
                    return customerRepository.save(customer);
                });
    }

    Customer getEntity(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    private void apply(Customer customer, CustomerDto.Request request) {
        customer.setName(request.name().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setPhone(request.phone());
        customer.setCompany(request.companyId() == null ? null : companyService.getEntity(request.companyId()));
    }
}
