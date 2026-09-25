package pt.cofinpro.prayingmantis.timesheets;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    /** A timesheet's entries with their project loaded, by project code, then day (T-6.1). */
    @Query("""
            select e from TimeEntry e join fetch e.project p
            where e.timesheet.id = :timesheetId
            order by p.code, e.workDate""")
    List<TimeEntry> findForTimesheet(@Param("timesheetId") Long timesheetId);

    /**
     * A user's entries with a work date in {@code from..to} (inclusive), whatever their week's status (decision
     * #18), with project and timesheet loaded, by day, then project code. For the monthly export (BE-8.2).
     */
    @Query("""
            select e from TimeEntry e join fetch e.project p join fetch e.timesheet t
            where t.user.id = :userId and e.workDate between :from and :to
            order by e.workDate, p.code""")
    List<TimeEntry> findForUserBetween(
            @Param("userId") Long userId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    /**
     * Deletes a timesheet's entries in one statement, straight away, so the new ones can reuse the same cells
     * (BE-6.3). {@code clearAutomatically} drops the deleted entities from the persistence context.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TimeEntry e where e.timesheet.id = :timesheetId")
    int deleteForTimesheet(@Param("timesheetId") Long timesheetId);
}
