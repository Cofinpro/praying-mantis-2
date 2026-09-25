package pt.cofinpro.prayingmantis.absences;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;
import pt.cofinpro.prayingmantis.users.User;

/**
 * A request for time off. {@code workingDays} is computed once, when the request is created, and stored
 * (decision #15). A user's pending and approved requests never overlap: the DB's exclusion constraint
 * ex_absence_requests_no_overlap enforces it (decision #14).
 */
@Entity
@Table(name = "absence_requests")
public class AbsenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "absence_type_id")
    private AbsenceType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_part", nullable = false)
    private DayPart startPart;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_part", nullable = false)
    private DayPart endPart;

    @Column(name = "working_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal workingDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AbsenceStatus status;

    private String reason;

    /** Null for types that need no approval (SICK). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment")
    private String decisionComment;

    // Set by the DB default; @Generated makes Hibernate read it back after the insert
    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AbsenceRequest() {
        // for JPA
    }

    /** A new request. Epic 3 decides its status and approver; this class doesn't check the rules. */
    public AbsenceRequest(
            User user,
            AbsenceType type,
            LocalDate startDate,
            DayPart startPart,
            LocalDate endDate,
            DayPart endPart,
            BigDecimal workingDays,
            AbsenceStatus status,
            User approver) {
        this.user = user;
        this.type = type;
        this.startDate = startDate;
        this.startPart = startPart;
        this.endDate = endDate;
        this.endPart = endPart;
        this.workingDays = workingDays;
        this.status = status;
        this.approver = approver;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AbsenceType getType() {
        return type;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public DayPart getStartPart() {
        return startPart;
    }

    public DayPart getEndPart() {
        return endPart;
    }

    public BigDecimal getWorkingDays() {
        return workingDays;
    }

    public AbsenceStatus getStatus() {
        return status;
    }

    public void setStatus(AbsenceStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public User getApprover() {
        return approver;
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
