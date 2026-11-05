package de.iu.projektverwaltung.repository;

import de.iu.projektverwaltung.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Abfragen für die mandantenbezogene Projektverwaltung. */
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByTenantIdOrderByIdAsc(Long tenantId);

    Optional<Project> findByIdAndTenantId(Long id, Long tenantId);
}
