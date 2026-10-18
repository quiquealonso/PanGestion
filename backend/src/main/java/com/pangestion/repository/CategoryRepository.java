package com.pangestion.repository;

import com.pangestion.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByCompanyIdOrderBySortOrderAscNameAsc(Long companyId);

    Optional<Category> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    /** Siguiente posicion libre para una categoria nueva de la empresa. */
    @Query("select coalesce(max(c.sortOrder) + 1, 0) from Category c where c.company.id = :companyId")
    int nextSortOrder(@Param("companyId") Long companyId);
}
