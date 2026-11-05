package de.iu.projektverwaltung.repository;

import de.iu.projektverwaltung.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

/** Speichert Mandanten und stellt die Standardzugriffe von Spring Data bereit. */
public interface TenantRepository extends JpaRepository<Tenant, Long> { }
