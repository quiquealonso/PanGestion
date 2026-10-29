package com.pangestion.repository;

import com.pangestion.model.TableSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    Optional<TableSession> findByIdAndCompanyId(Long id, Long companyId);

    /** Mesas abiertas de la empresa, con las mesas que ocupan. */
    @Query("""
            select distinct s from TableSession s left join fetch s.tables
            where s.company.id = :companyId and s.status = com.pangestion.model.SessionStatus.OPEN
            """)
    List<TableSession> findOpen(@Param("companyId") Long companyId);

    /** Servicio abierto que ocupa la mesa, sin contar el indicado (-1 = ninguno). */
    @Query("""
            select s from TableSession s join s.tables t
            where t.id = :tableId and s.status = com.pangestion.model.SessionStatus.OPEN and s.id <> :excludeId
            """)
    Optional<TableSession> findOpenWithTable(@Param("tableId") Long tableId, @Param("excludeId") Long excludeId);

    /** Mesas cerradas en un intervalo (caja del dia), de la ultima a la primera. */
    @Query("""
            select s from TableSession s
            where s.company.id = :companyId and s.status = com.pangestion.model.SessionStatus.CLOSED
              and s.closedAt >= :from and s.closedAt < :to
            order by s.closedAt desc
            """)
    List<TableSession> findClosedBetween(@Param("companyId") Long companyId,
                                         @Param("from") java.time.LocalDateTime from,
                                         @Param("to") java.time.LocalDateTime to);

    /** Hay alguna mesa ocupada en la zona (para no borrarla en pleno servicio). */
    @Query("""
            select count(s) > 0 from TableSession s join s.tables t
            where t.area.id = :areaId and s.status = com.pangestion.model.SessionStatus.OPEN
            """)
    boolean existsOpenInArea(@Param("areaId") Long areaId);
}
