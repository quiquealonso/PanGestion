package com.pangestion.repository;

import com.pangestion.model.Role;
import com.pangestion.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Todos los usuarios (para SUPERADMIN). */
    List<User> findAllByOrderByNameAsc();

    /** Usuarios de una empresa (para su ADMIN). */
    List<User> findAllByCompanyIdOrderByNameAsc(Long companyId);

    Optional<User> findByIdAndCompanyId(Long id, Long companyId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByCompanyIdAndRoleAndActiveTrue(Long companyId, Role role);

    long countByRoleAndActiveTrue(Role role);

    long countByCompanyId(Long companyId);
}
