package de.iu.projektverwaltung.repository;

import de.iu.projektverwaltung.model.AppUser;
import de.iu.projektverwaltung.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Sucht Konten für Anmeldung, Mitgliederzuordnung und Rollenverwaltung. */
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);

    List<AppUser> findAllByTenantIdOrderByDisplayNameAsc(Long tenantId);

    long countByTenantIdAndRole(Long tenantId, Role role);
}
