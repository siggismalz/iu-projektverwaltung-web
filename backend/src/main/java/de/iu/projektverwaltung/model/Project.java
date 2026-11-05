package de.iu.projektverwaltung.model;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "projects", indexes = @Index(name = "idx_project_tenant", columnList = "tenant_id"))
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false)
    private boolean archived;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToMany
    @JoinTable(name = "project_members", joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
    private Set<AppUser> members = new LinkedHashSet<>();

    protected Project() { }
    public Project(String name, String description, Tenant tenant, Set<AppUser> members) {
        this.name = name; this.description = description; this.tenant = tenant;
        this.members.addAll(members);
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isArchived() { return archived; }
    public Tenant getTenant() { return tenant; }
    public Set<AppUser> getMembers() { return members; }
    public void update(String name, String description, boolean archived, Set<AppUser> members) {
        this.name = name; this.description = description; this.archived = archived;
        this.members.clear(); this.members.addAll(members);
    }
}
