package pt.cofinpro.prayingmantis.absences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference data from the Liquibase changelog; the app only reads it. */
@Entity
@Table(name = "absence_types")
public class AbsenceType {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private AbsenceTypeCode code;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_paid", nullable = false)
    private boolean paid;

    @Column(name = "deducts_from_balance", nullable = false)
    private boolean deductsFromBalance;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    protected AbsenceType() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public AbsenceTypeCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isDeductsFromBalance() {
        return deductsFromBalance;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }
}
