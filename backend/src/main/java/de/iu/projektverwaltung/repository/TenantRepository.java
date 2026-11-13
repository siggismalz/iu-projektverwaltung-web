package de.iu.projektverwaltung.repository;

import de.iu.projektverwaltung.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> { }
