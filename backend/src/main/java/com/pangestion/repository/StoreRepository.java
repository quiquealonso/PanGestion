package com.pangestion.repository;

import com.pangestion.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByOrderByNameAsc();

    List<Store> findByActiveTrueOrderByNameAsc();
}
