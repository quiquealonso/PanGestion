package com.pangestion.repository;

import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.Store;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Listado con filtros opcionales. El texto busca por cliente o por numero de pedido. */
    @EntityGraph(attributePaths = {"customer", "store"})
    @Query("""
            select o from Order o
            where (lower(o.customer.name) like :search or cast(o.id as String) like :search)
              and (:status is null or o.status = :status)
              and (:storeId is null or o.store.id = :storeId)
              and (:fromDate is null or o.pickupDate >= :fromDate)
              and (:toDate is null or o.pickupDate <= :toDate)
            order by o.pickupDate desc, o.pickupTime desc
            """)
    List<Order> search(@Param("search") String search,
                       @Param("status") OrderStatus status,
                       @Param("storeId") Long storeId,
                       @Param("fromDate") LocalDate fromDate,
                       @Param("toDate") LocalDate toDate);

    @EntityGraph(attributePaths = {"customer", "store"})
    List<Order> findByCustomerIdOrderByPickupDateDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "store"})
    List<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(OrderStatus status);

    long countByPickupDateAndStatusNot(LocalDate date, OrderStatus status);

    /** Pedidos que ocupan una franja (los cancelados no cuentan). */
    @Query("""
            select count(o) from Order o
            where o.store = :store and o.pickupDate = :date and o.pickupTime = :time
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
              and o.id <> :excludeId
            """)
    long countInSlot(@Param("store") Store store,
                     @Param("date") LocalDate date,
                     @Param("time") LocalTime time,
                     @Param("excludeId") Long excludeId);

    @Query("""
            select coalesce(sum(o.total), 0) from Order o
            where o.pickupDate between :fromDate and :toDate
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
            """)
    BigDecimal sumRevenue(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("""
            select count(o) from Order o
            where o.pickupDate between :fromDate and :toDate
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
            """)
    long countBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    boolean existsByCustomerId(Long customerId);

    boolean existsByStoreId(Long storeId);
}
