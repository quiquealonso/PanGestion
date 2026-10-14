package com.pangestion.service;

import com.pangestion.dto.CompanyDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Company;
import com.pangestion.repository.CompanyRepository;
import com.pangestion.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CompanyDto.Response> findAll() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyDto.Response findById(Long id) {
        return toResponse(getEntity(id));
    }

    public CompanyDto.Response create(CompanyDto.Request request) {
        if (companyRepository.existsByTaxIdIgnoreCase(request.taxId())) {
            throw new BusinessException("Ya existe una empresa con el CIF " + request.taxId());
        }
        Company company = new Company();
        apply(company, request);
        return toResponse(companyRepository.save(company));
    }

    public CompanyDto.Response update(Long id, CompanyDto.Request request) {
        Company company = getEntity(id);
        if (companyRepository.existsByTaxIdIgnoreCaseAndIdNot(request.taxId(), id)) {
            throw new BusinessException("Ya existe una empresa con el CIF " + request.taxId());
        }
        apply(company, request);
        return toResponse(company);
    }

    public void delete(Long id) {
        Company company = getEntity(id);
        if (customerRepository.countByCompanyId(id) > 0) {
            throw new BusinessException("No se puede eliminar una empresa que tiene clientes asociados");
        }
        companyRepository.delete(company);
    }

    Company getEntity(Long id) {
        return companyRepository.findById(id).orElseThrow(() -> new NotFoundException("Empresa", id));
    }

    private void apply(Company company, CompanyDto.Request request) {
        company.setName(request.name().trim());
        company.setTaxId(request.taxId().trim().toUpperCase());
        company.setEmail(request.email());
        company.setPhone(request.phone());
        company.setAddress(request.address());
    }

    private CompanyDto.Response toResponse(Company company) {
        return CompanyDto.Response.from(company, customerRepository.countByCompanyId(company.getId()));
    }
}
