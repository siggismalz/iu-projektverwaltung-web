package de.iu.projektverwaltung.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String username;
    @Column(nullable = false, length = 80)
    private String displayName;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    protected AppUser() { }
    public AppUser(String username, String displayName, String passwordHash, Role role, Tenant tenant) {
        this.username = username; this.displayName = displayName; this.passwordHash = passwordHash;
        this.role = role; this.tenant = tenant;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Tenant getTenant() { return tenant; }
    public void setRole(Role role) { this.role = role; }
}
