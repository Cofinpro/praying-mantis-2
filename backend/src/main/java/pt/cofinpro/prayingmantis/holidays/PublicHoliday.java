package pt.cofinpro.prayingmantis.holidays;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A day that isn't a working day (decision #15): one of Portugal's national holidays, one row per date. */
@Entity
@Table(name = "public_holidays")
public class PublicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false)
    private String name;

    protected PublicHoliday() {
        // for JPA
    }

    public PublicHoliday(LocalDate date, String name) {
        this.date = date;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    /** BE-9.4. Existing absence requests keep their stored working days (decision #15). */
    public void update(LocalDate date, String name) {
        this.date = date;
        this.name = name;
    }
}
