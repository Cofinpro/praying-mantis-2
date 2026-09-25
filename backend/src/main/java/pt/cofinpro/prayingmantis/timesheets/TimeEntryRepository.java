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
     * Deletes a timesheet's entries in one statement, straight away, so the new ones can reuse the same cells
     * (BE-6.3). {@code clearAutomatically} drops the deleted entities from the persistence context.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TimeEntry e where e.timesheet.id = :timesheetId")
    int deleteForTimesheet(@Param("timesheetId") Long timesheetId);
}
