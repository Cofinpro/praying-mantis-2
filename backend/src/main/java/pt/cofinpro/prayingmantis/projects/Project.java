package pt.cofinpro.prayingmantis.projects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pt.cofinpro.prayingmantis.users.Client;

/** A project hours are booked on. {@code client} is null for internal projects. Only active ones take new hours. */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private Client client;

    @Column(name = "is_billable", nullable = false)
    private boolean billable;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Project() {
        // for JPA
    }

    public Project(String code, String name, Client client, boolean billable, boolean active) {
        this.code = code;
        this.name = name;
        this.client = client;
        this.billable = billable;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Client getClient() {
        return client;
    }

    public boolean isBillable() {
        return billable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Everything the admin can change (BE-9.3). The code is stored in upper case. */
    public void update(String code, String name, Client client, boolean billable, boolean active) {
        this.code = code;
        this.name = name;
        this.client = client;
        this.billable = billable;
        this.active = active;
    }
}
