package pt.cofinpro.prayingmantis.timesheets;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    /** A user's week, with the approver loaded for the API. */
    @Query("select t from Timesheet t left join fetch t.approver where t.user.id = :userId and t.weekStart = :weekStart")
    Optional<Timesheet> findWeek(@Param("userId") Long userId, @Param("weekStart") LocalDate weekStart);
}
