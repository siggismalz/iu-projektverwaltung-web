package de.iu.projektverwaltung.config;

import de.iu.projektverwaltung.model.*;
import de.iu.projektverwaltung.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/** Nur erfundene Lehrdaten. Vorhandene Daten werden beim Neustart nicht überschrieben. */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true", matchIfMissing = true)
public class DemoData implements CommandLineRunner {
    private final TenantRepository tenants;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final PasswordEncoder passwords;

    public DemoData(TenantRepository tenants, UserRepository users, ProjectRepository projects,
                    TaskRepository tasks, PasswordEncoder passwords) {
        this.tenants = tenants;
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (tenants.count() != 0) {
            return;
        }

        Tenant demo = tenants.save(new Tenant("Muster IT GmbH"));
        Tenant second = tenants.save(new Tenant("Zweiter Demomandant"));
        String hash = passwords.encode("Demo2026!");
        users.save(new AppUser("admin", "Demo Administration", hash, Role.ADMIN, demo));
        AppUser lead = users.save(new AppUser("leitung", "Demo Projektleitung", hash, Role.LEAD, demo));
        AppUser member = users.save(new AppUser("mitarbeiter", "Demo Mitarbeit", hash, Role.MEMBER, demo));
        AppUser foreign = users.save(new AppUser("fremd", "Demo Fremdmandant", hash, Role.ADMIN, second));
        Project intranet = projects.save(new Project("Intranet-Relaunch", "Internes Portal für das erfundene Kundenprojekt neu aufbauen.",
                demo, new LinkedHashSet<>(List.of(lead, member))));
        Project portal = projects.save(new Project("Kundenportal", "Anforderungen und Prototyp für ein Kundenportal vorbereiten.",
                demo, new LinkedHashSet<>(List.of(lead))));
        Project other = projects.save(new Project("Fremdes Projekt", "Daten dieses Projekts gehören ausschließlich zum zweiten Mandanten.",
                second, new LinkedHashSet<>(List.of(foreign))));
        tasks.saveAll(List.of(
                new Task("Anforderungen abstimmen", "Anforderungsliste mit dem fiktiven Auftraggeber strukturieren.", TaskStatus.DONE, intranet, lead),
                new Task("Seitennavigation entwerfen", "Entwurf für Übersicht und Detailseiten erstellen.", TaskStatus.IN_PROGRESS, intranet, member),
                new Task("Anmeldung testen", "Anmeldung und ungültige Zugriffe prüfen.", TaskStatus.OPEN, intranet, member),
                new Task("Datenmodell prüfen", "Projekt- und Aufgabendaten auf Vollständigkeit prüfen.", TaskStatus.DONE, portal, lead),
                new Task("Oberfläche skizzieren", "Einen ersten Entwurf der Portalübersicht erstellen.", TaskStatus.OPEN, portal, lead),
                new Task("Interne Aufgabe", "Nur für den zweiten Mandanten sichtbar.", TaskStatus.OPEN, other, foreign)
        ));
    }
}
