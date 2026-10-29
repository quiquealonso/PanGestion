package com.pangestion.repository;

import com.pangestion.model.ServiceLine;
import com.pangestion.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceLineRepository extends JpaRepository<ServiceLine, Long> {

    /** Pantalla de cocina: productos de una estacion, de mesas abiertas, que aun no se han servido. */
    @Query("""
            select l from ServiceLine l
            join fetch l.product
            join fetch l.session s
            where s.company.id = :companyId and s.status = com.pangestion.model.SessionStatus.OPEN
              and l.station = :station and l.status <> com.pangestion.model.ItemStatus.SERVIDO
            """)
    List<ServiceLine> findForKitchen(@Param("companyId") Long companyId, @Param("station") Station station);

    Optional<ServiceLine> findByIdAndSessionCompanyId(Long id, Long companyId);
}
