package com.pangestion.repository;

import com.pangestion.model.Company;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Pedidos a domicilio de un dia (sin los cancelados), por hora de entrega: la pantalla del repartidor. */
    @Query("""
            select o from Order o
            where o.company.id = :companyId and o.pickupDate = :date
              and o.type = com.pangestion.model.OrderType.DOMICILIO
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
            order by o.pickupTime, o.id
            """)
    List<Order> findForDelivery(@Param("companyId") Long companyId, @Param("date") LocalDate date);

    /** Pedidos de una empresa con filtros opcionales. El texto busca por cliente o por numero de pedido. */
    @EntityGraph(attributePaths = "customer")
    @Query("""
            select o from Order o
            where o.company.id = :companyId
              and (lower(o.customer.name) like :search or cast(o.id as String) like :search)
              and (:status is null or o.status = :status)
              and (:fromDate is null or o.pickupDate >= :fromDate)
              and (:toDate is null or o.pickupDate <= :toDate)
            order by o.pickupDate desc, o.pickupTime desc
            """)
    List<Order> search(@Param("companyId") Long companyId,
                       @Param("search") String search,
                       @Param("status") OrderStatus status,
                       @Param("fromDate") LocalDate fromDate,
                       @Param("toDate") LocalDate toDate);

    Optional<Order> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = "customer")
    List<Order> findByCustomerIdOrderByPickupDateDesc(Long customerId);

    @EntityGraph(attributePaths = "customer")
    List<Order> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    long countByCompanyIdAndStatus(Long companyId, OrderStatus status);

    List<Order> findByCompanyIdAndPickupDateAndStatus(Long companyId, LocalDate pickupDate, OrderStatus status);

    long countByCompanyIdAndPickupDateAndStatusNot(Long companyId, LocalDate date, OrderStatus status);

    long countByCompanyIdAndPickupDateAndStatusIn(Long companyId, LocalDate date, java.util.Collection<OrderStatus> statuses);

    /** Pedidos que ocupan una franja de la empresa (los cancelados no cuentan). */
    @Query("""
            select count(o) from Order o
            where o.company = :company and o.pickupDate = :date and o.pickupTime = :time
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
              and o.id <> :excludeId
            """)
    long countInSlot(@Param("company") Company company,
                     @Param("date") LocalDate date,
                     @Param("time") LocalTime time,
                     @Param("excludeId") Long excludeId);

    @Query("""
            select coalesce(sum(o.total), 0) from Order o
            where o.company.id = :companyId and o.pickupDate between :fromDate and :toDate
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
            """)
    BigDecimal sumRevenue(@Param("companyId") Long companyId,
                          @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("""
            select count(o) from Order o
            where o.company.id = :companyId and o.pickupDate between :fromDate and :toDate
              and o.status <> com.pangestion.model.OrderStatus.CANCELADO
            """)
    long countBetween(@Param("companyId") Long companyId,
                      @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    boolean existsByCustomerId(Long customerId);

    /** Resumen de pedidos de cada cliente de la empresa (los cancelados no cuentan). */
    interface CustomerOrderStats {
        Long getCustomerId();
        long getOrderCount();
        BigDecimal getTotalSpent();
        LocalDate getLastOrderDate();
    }

    @Query("""
            select o.customer.id as customerId, count(o) as orderCount, coalesce(sum(o.total), 0) as totalSpent,
                   max(o.pickupDate) as lastOrderDate
            from Order o
            where o.company.id = :companyId and o.status <> com.pangestion.model.OrderStatus.CANCELADO
            group by o.customer.id
            """)
    List<CustomerOrderStats> customerStats(@Param("companyId") Long companyId);

    long countByCompanyId(Long companyId);
}
