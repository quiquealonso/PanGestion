package com.pangestion.repository;

import com.pangestion.model.DiningArea;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiningAreaRepository extends JpaRepository<DiningArea, Long> {

    @EntityGraph(attributePaths = "tables")
    List<DiningArea> findAllByCompanyIdOrderBySortOrderAscNameAsc(Long companyId);

    Optional<DiningArea> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    /** Siguiente posicion libre para una zona nueva de la empresa. */
    @Query("select coalesce(max(a.sortOrder) + 1, 0) from DiningArea a where a.company.id = :companyId")
    int nextSortOrder(@Param("companyId") Long companyId);
}
