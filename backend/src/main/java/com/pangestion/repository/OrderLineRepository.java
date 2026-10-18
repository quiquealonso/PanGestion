package com.pangestion.repository;

import com.pangestion.model.OrderLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    boolean existsByProductId(Long productId);

    /** Lineas de los pedidos de un dia, salvo los estados indicados (para la hoja de produccion). */
    @Query("""
            select l from OrderLine l
            join fetch l.product p
            join fetch p.category
            join fetch l.order o
            where o.company.id = :companyId
              and o.pickupDate = :date
              and o.status not in :excluded
            """)
    List<OrderLine> findForProduction(@Param("companyId") Long companyId,
                                      @Param("date") java.time.LocalDate date,
                                      @Param("excluded") java.util.Collection<com.pangestion.model.OrderStatus> excluded);

    /** Unidades vendidas por dia de recogida en un rango. Cada fila: [fecha, unidades]. Sin cancelados. */
    @Query("""
            select l.order.pickupDate, sum(l.quantity) from OrderLine l
            where l.order.company.id = :companyId
              and l.order.pickupDate between :fromDate and :toDate
              and l.order.status <> com.pangestion.model.OrderStatus.CANCELADO
            group by l.order.pickupDate
            """)
    List<Object[]> unitsPerDay(@Param("companyId") Long companyId,
                               @Param("fromDate") java.time.LocalDate fromDate,
                               @Param("toDate") java.time.LocalDate toDate);

    /** Productos mas vendidos de una empresa. Cada fila: [nombre del producto, unidades vendidas]. */
    @Query("""
            select l.product.name, sum(l.quantity) from OrderLine l
            where l.order.company.id = :companyId
              and l.order.status <> com.pangestion.model.OrderStatus.CANCELADO
            group by l.product.name
            order by sum(l.quantity) desc
            """)
    List<Object[]> findTopProducts(@Param("companyId") Long companyId, Pageable pageable);
}
