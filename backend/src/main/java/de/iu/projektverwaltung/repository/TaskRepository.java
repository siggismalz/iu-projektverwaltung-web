package de.iu.projektverwaltung.repository;

import de.iu.projektverwaltung.model.Task;
import de.iu.projektverwaltung.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Lädt und zählt Aufgaben; direkte Aufgabenaufrufe werden nach Mandant begrenzt. */
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByProjectIdOrderByIdAsc(Long projectId);

    Optional<Task> findByIdAndProjectTenantId(Long id, Long tenantId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    void deleteAllByProjectId(Long projectId);
}
