package com.pangestion.repository;

import com.pangestion.model.Customer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Busca por nombre, email o nombre de empresa. */
    @EntityGraph(attributePaths = "company")
    @Query("""
            select c from Customer c left join c.company co
            where lower(c.name) like :search
               or lower(c.email) like :search
               or lower(co.name) like :search
            order by c.name
            """)
    List<Customer> search(@Param("search") String search);

    Optional<Customer> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByCompanyId(Long companyId);
}
