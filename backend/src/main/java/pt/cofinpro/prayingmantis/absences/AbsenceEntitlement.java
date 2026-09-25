package pt.cofinpro.prayingmantis.absences;

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
import pt.cofinpro.prayingmantis.users.User;

/** How many days of one type a user gets in one year. At most one per user, type and year (DB unique). */
@Entity
@Table(name = "absence_entitlements")
public class AbsenceEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "absence_type_id")
    private AbsenceType type;

    @Column(nullable = false)
    private int year;

    @Column(name = "entitled_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal entitledDays;

    @Column(name = "carried_over_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal carriedOverDays;

    protected AbsenceEntitlement() {
        // for JPA
    }

    public AbsenceEntitlement(User user, AbsenceType type, int year, BigDecimal entitledDays, BigDecimal carriedOverDays) {
        this.user = user;
        this.type = type;
        this.year = year;
        this.entitledDays = entitledDays;
        this.carriedOverDays = carriedOverDays;
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

    public int getYear() {
        return year;
    }

    public BigDecimal getEntitledDays() {
        return entitledDays;
    }

    public void setEntitledDays(BigDecimal entitledDays) {
        this.entitledDays = entitledDays;
    }

    public BigDecimal getCarriedOverDays() {
        return carriedOverDays;
    }

    public void setCarriedOverDays(BigDecimal carriedOverDays) {
        this.carriedOverDays = carriedOverDays;
    }
}
