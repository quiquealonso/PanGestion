package com.pangestion.repository;

import com.pangestion.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Clientes de una empresa; busca por nombre, email o razon social. */
    @Query("""
            select c from Customer c
            where c.company.id = :companyId
              and (lower(c.name) like :search
                   or lower(c.email) like :search
                   or lower(coalesce(c.businessName, '')) like :search)
            order by c.name
            """)
    List<Customer> search(@Param("companyId") Long companyId, @Param("search") String search);

    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Customer> findByCompanyIdAndEmailIgnoreCase(Long companyId, String email);

    boolean existsByCompanyIdAndEmailIgnoreCase(Long companyId, String email);

    boolean existsByCompanyIdAndEmailIgnoreCaseAndIdNot(Long companyId, String email, Long id);

    long countByCompanyId(Long companyId);
}
