package com.pangestion.repository;

import com.pangestion.model.OrderLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    boolean existsByProductId(Long productId);

    /** Productos mas vendidos. Cada fila: [nombre del producto, unidades vendidas]. */
    @Query("""
            select l.product.name, sum(l.quantity) from OrderLine l
            where l.order.status <> com.pangestion.model.OrderStatus.CANCELADO
            group by l.product.name
            order by sum(l.quantity) desc
            """)
    List<Object[]> findTopProducts(Pageable pageable);
}
