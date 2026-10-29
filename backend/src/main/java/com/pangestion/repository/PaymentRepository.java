package com.pangestion.repository;

import com.pangestion.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Caja: total cobrado por forma de pago en un intervalo. Cada fila: [forma de pago, importe]. */
    @Query("""
            select p.method, sum(p.amount) from Payment p
            where p.session.company.id = :companyId and p.paidAt >= :from and p.paidAt < :to
            group by p.method
            """)
    List<Object[]> totalsByMethod(@Param("companyId") Long companyId,
                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
