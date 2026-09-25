package pt.cofinpro.prayingmantis.timesheets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import pt.cofinpro.prayingmantis.projects.Project;

/** Hours on one project on one day of a timesheet's week: one grid cell (uq_time_entries_cell). */
@Entity
@Table(name = "time_entries")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timesheet_id")
    private Timesheet timesheet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal hours;

    private String description;

    protected TimeEntry() {
        // for JPA
    }

    public TimeEntry(Timesheet timesheet, Project project, LocalDate workDate, BigDecimal hours, String description) {
        this.timesheet = timesheet;
        this.project = project;
        this.workDate = workDate;
        this.hours = hours;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Timesheet getTimesheet() {
        return timesheet;
    }

    public Project getProject() {
        return project;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public String getDescription() {
        return description;
    }
}
