package pt.cofinpro.prayingmantis.timesheets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;
import pt.cofinpro.prayingmantis.users.User;

/**
 * One user's week, starting on a Monday (DB check). Its entries are {@link TimeEntry} rows pointing here,
 * deliberately not a mapped collection: saving a week deletes and re-inserts them (BE-6.3), and Hibernate
 * flushes a collection's inserts before its deletes, which would hit uq_time_entries_cell.
 */
@Entity
@Table(name = "timesheets")
public class Timesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status;

    /** Set on submit (BE-6.4). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment")
    private String decisionComment;

    // Set by the DB default; @Generated makes Hibernate read it back after the insert
    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Timesheet() {
        // for JPA
    }

    /** A new, empty draft. */
    public Timesheet(User user, LocalDate weekStart) {
        this.user = user;
        this.weekStart = weekStart;
        this.status = TimesheetStatus.DRAFT;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public TimesheetStatus getStatus() {
        return status;
    }

    public void setStatus(TimesheetStatus status) {
        this.status = status;
    }

    public User getApprover() {
        return approver;
    }

    public void setApprover(User approver) {
        this.approver = approver;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
