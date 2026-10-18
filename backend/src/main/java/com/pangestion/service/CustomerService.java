package com.pangestion.service;

import com.pangestion.dto.CustomerDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Company;
import com.pangestion.model.Customer;
import com.pangestion.repository.CustomerRepository;
import com.pangestion.repository.OrderRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Clientes de la empresa del usuario logueado. */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<CustomerDto.Response> search(String search) {
        return customerRepository.search(currentUser.companyId(), SearchUtils.likePattern(search)).stream()
                .map(CustomerDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerDto.Response findById(Long id) {
        return CustomerDto.Response.from(getEntity(id));
    }

    public CustomerDto.Response create(CustomerDto.Request request) {
        Long companyId = currentUser.companyId();
        if (customerRepository.existsByCompanyIdAndEmailIgnoreCase(companyId, request.email())) {
            throw new BusinessException("Ya existe un cliente con el email " + request.email());
        }
        Customer customer = new Customer();
        customer.setCompany(companyService.current());
        apply(customer, request);
        return CustomerDto.Response.from(customerRepository.save(customer));
    }

    public CustomerDto.Response update(Long id, CustomerDto.Request request) {
        Customer customer = getEntity(id);
        if (customerRepository.existsByCompanyIdAndEmailIgnoreCaseAndIdNot(currentUser.companyId(), request.email(), id)) {
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
     * Para la web publica: busca el cliente en esa panaderia por su email y actualiza su nombre y telefono,
     * o lo crea si es la primera vez que pide alli.
     */
    Customer findOrCreate(Company company, CustomerDto.Request request) {
        return customerRepository.findByCompanyIdAndEmailIgnoreCase(company.getId(), request.email().trim())
                .map(existing -> {
                    existing.setName(request.name().trim());
                    existing.setPhone(request.phone());
                    return existing;
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setCompany(company);
                    apply(customer, request);
                    return customerRepository.save(customer);
                });
    }

    Customer getEntity(Long id) {
        return customerRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    private void apply(Customer customer, CustomerDto.Request request) {
        customer.setName(request.name().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setPhone(request.phone());
        customer.setBusinessName(blankToNull(request.businessName()));
        customer.setTaxId(blankToNull(request.taxId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
