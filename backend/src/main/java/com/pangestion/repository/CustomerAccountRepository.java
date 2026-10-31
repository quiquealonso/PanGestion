package com.pangestion.repository;

import com.pangestion.model.CustomerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    Optional<CustomerAccount> findByEmailIgnoreCase(String email);

    Optional<CustomerAccount> findByVerificationTokenHash(String verificationTokenHash);
}
