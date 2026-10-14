package com.pangestion.repository;

import com.pangestion.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findAllByOrderByNameAsc();

    boolean existsByTaxIdIgnoreCase(String taxId);

    boolean existsByTaxIdIgnoreCaseAndIdNot(String taxId, Long id);
}
