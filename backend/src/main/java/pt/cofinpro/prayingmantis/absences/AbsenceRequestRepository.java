package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    /** A user's requests in the given statuses that overlap {@code from..to} (inclusive), with their type loaded. */
    @Query("""
            select r from AbsenceRequest r join fetch r.type
            where r.user.id = :userId and r.status in :statuses and r.startDate <= :to and r.endDate >= :from""")
    List<AbsenceRequest> findWithTypeOverlapping(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AbsenceStatus> statuses);
}
