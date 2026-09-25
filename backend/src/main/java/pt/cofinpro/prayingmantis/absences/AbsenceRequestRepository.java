package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
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

    /**
     * The requests an approver decides on (BE-5.1), with type, requester and approver loaded. The order comes
     * from {@code sort}, e.g. by start date.
     */
    @Query("""
            select r from AbsenceRequest r join fetch r.type join fetch r.user join fetch r.approver a
            where a.id = :approverId and r.status = :status""")
    List<AbsenceRequest> findForApprover(
            @Param("approverId") Long approverId, @Param("status") AbsenceStatus status, Sort sort);

    /** Any request by id, with type, requester and approver loaded, for deciding on it (BE-5.2). */
    @Query("""
            select r from AbsenceRequest r join fetch r.type join fetch r.user left join fetch r.approver
            where r.id = :id""")
    Optional<AbsenceRequest> findWithPeople(@Param("id") Long id);

    /** One of the user's own requests, with the type and approver loaded; empty if it's someone else's. */
    @Query("""
            select r from AbsenceRequest r join fetch r.type left join fetch r.approver
            where r.id = :id and r.user.id = :userId""")
    Optional<AbsenceRequest> findOwn(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * The requests of several users in the given statuses that overlap {@code from..to} (inclusive), with the
     * type loaded, by start date (the team calendar, BE-5.3).
     */
    @Query("""
            select r from AbsenceRequest r join fetch r.type
            where r.user.id in :userIds and r.status in :statuses and r.startDate <= :to and r.endDate >= :from
            order by r.startDate, r.id""")
    List<AbsenceRequest> findForUsersOverlapping(
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AbsenceStatus> statuses);

    /** Does the user have a request in the given statuses that overlaps {@code from..to} (inclusive)? */
    @Query("""
            select count(r) > 0 from AbsenceRequest r
            where r.user.id = :userId and r.status in :statuses and r.startDate <= :to and r.endDate >= :from""")
    boolean existsOverlapping(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<AbsenceStatus> statuses);

    /**
     * Every request of a user that overlaps {@code from..to} (inclusive), in any status, with the type and
     * the approver loaded for the API, ordered by start date (BE-2.3).
     */
    @Query("""
            select r from AbsenceRequest r join fetch r.type left join fetch r.approver
            where r.user.id = :userId and r.startDate <= :to and r.endDate >= :from
            order by r.startDate, r.id""")
    List<AbsenceRequest> findForCalendar(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
