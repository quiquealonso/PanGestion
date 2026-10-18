package com.pangestion.repository;

import com.pangestion.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Productos de una empresa con filtros opcionales: texto (nombre), categoria y estado activo. */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where p.company.id = :companyId
              and lower(p.name) like :search
              and (:categoryId is null or p.category.id = :categoryId)
              and (:active is null or p.active = :active)
            order by p.category.sortOrder, p.category.name, p.sortOrder, p.name
            """)
    List<Product> search(@Param("companyId") Long companyId,
                         @Param("search") String search,
                         @Param("categoryId") Long categoryId,
                         @Param("active") Boolean active);

    Optional<Product> findByIdAndCompanyId(Long id, Long companyId);

    /** Siguiente posicion libre dentro de una categoria. */
    @Query("select coalesce(max(p.sortOrder) + 1, 0) from Product p where p.category.id = :categoryId")
    int nextSortOrder(@Param("categoryId") Long categoryId);

    long countByCompanyIdAndActiveTrue(Long companyId);

    long countByCategoryId(Long categoryId);

    /** Productos que llevan un ingrediente. */
    long countByIngredientsId(Long ingredientId);
}
