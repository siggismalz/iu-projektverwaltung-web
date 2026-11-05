package de.iu.projektverwaltung.model;

import jakarta.persistence.*;

/** Ein Mandant trennt die Daten eines IT-Dienstleisters von anderen Mandanten. */
@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    // JPA benötigt diesen leeren Konstruktor zum Laden aus der Datenbank.
    protected Tenant() { }

    public Tenant(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
