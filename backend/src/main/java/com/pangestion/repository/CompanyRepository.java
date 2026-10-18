package com.pangestion.repository;

import com.pangestion.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findAllByOrderByNameAsc();

    /** Empresas que aceptan pedidos (web publica). */
    List<Company> findByActiveTrueOrderByNameAsc();

    boolean existsByTaxIdIgnoreCase(String taxId);

    boolean existsByTaxIdIgnoreCaseAndIdNot(String taxId, Long id);
}
