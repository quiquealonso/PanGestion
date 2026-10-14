package com.pangestion.repository;

import com.pangestion.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Busqueda con filtros opcionales: texto (nombre), categoria y estado activo. */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where lower(p.name) like :search
              and (:categoryId is null or p.category.id = :categoryId)
              and (:active is null or p.active = :active)
            order by p.name
            """)
    List<Product> search(@Param("search") String search,
                         @Param("categoryId") Long categoryId,
                         @Param("active") Boolean active);

    long countByActiveTrue();

    long countByCategoryId(Long categoryId);
}
