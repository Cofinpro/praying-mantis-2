package pt.cofinpro.prayingmantis.timesheets;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    /** A user's week, with the approver loaded for the API. */
    @Query("select t from Timesheet t left join fetch t.approver where t.user.id = :userId and t.weekStart = :weekStart")
    Optional<Timesheet> findWeek(@Param("userId") Long userId, @Param("weekStart") LocalDate weekStart);

    /** The weeks an approver decides on (BE-7.1), with user and approver loaded. The order comes from {@code sort}. */
    @Query("""
            select t from Timesheet t join fetch t.user join fetch t.approver a
            where a.id = :approverId and t.status = :status""")
    List<Timesheet> findForApprover(@Param("approverId") Long approverId, @Param("status") TimesheetStatus status, Sort sort);

    /** Any week by id, with user and approver loaded, for deciding on it (BE-7.2). */
    @Query("select t from Timesheet t join fetch t.user left join fetch t.approver where t.id = :id")
    Optional<Timesheet> findWithPeople(@Param("id") Long id);
}
