package com.pangestion.repository;

import com.pangestion.model.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    Optional<DiningTable> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    long countByCompanyId(Long companyId);
}
