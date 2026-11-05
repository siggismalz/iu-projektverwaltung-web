package de.iu.projektverwaltung.controller;

import de.iu.projektverwaltung.model.Role;
import de.iu.projektverwaltung.model.TaskStatus;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

/** Bewusst nur API-Daten: JPA-Entitäten und Passworthashes werden nie serialisiert. */
public final class ApiModels {
    private ApiModels() { }

    public record UserView(Long id, String username, String displayName, Role role) { }
    public record SessionView(Long id, String username, String displayName, Role role, String tenantName) { }
    public record NewUser(
            @NotBlank(message = "Benutzername fehlt.")
            @Pattern(regexp = "[a-zA-Z0-9._-]{3,40}", message = "Benutzername: 3–40 Buchstaben, Ziffern, Punkt, Bindestrich oder Unterstrich.") String username,
            @NotBlank(message = "Anzeigename fehlt.") @Size(max = 80, message = "Anzeigename: höchstens 80 Zeichen.") String displayName,
            @NotBlank(message = "Passwort fehlt.") @Size(min = 8, max = 72, message = "Passwort: 8–72 Zeichen.") String password,
            @NotNull(message = "Rolle fehlt.") Role role) { }
    public record RoleChange(@NotNull(message = "Rolle fehlt.") Role role) { }
    public record ProjectInput(
            @NotBlank(message = "Projektname fehlt.") @Size(max = 120, message = "Projektname: höchstens 120 Zeichen.") String name,
            @NotNull(message = "Beschreibung fehlt.") @Size(max = 2000, message = "Beschreibung: höchstens 2000 Zeichen.") String description,
            @NotNull(message = "Mitgliederliste fehlt.") Set<@NotNull Long> memberIds,
            boolean archived) { }
    public record ProjectView(Long id, String name, String description, boolean archived,
                              List<Long> memberIds, List<String> memberNames,
                              long totalTasks, long doneTasks, int progress) { }
    public record TaskInput(
            @NotBlank(message = "Aufgabentitel fehlt.") @Size(max = 160, message = "Aufgabentitel: höchstens 160 Zeichen.") String title,
            @NotNull(message = "Beschreibung fehlt.") @Size(max = 2000, message = "Beschreibung: höchstens 2000 Zeichen.") String description,
            @NotNull(message = "Status fehlt.") TaskStatus status,
            Long assigneeId) { }
    public record TaskView(Long id, String title, String description, TaskStatus status,
                           Long assigneeId, String assigneeName, Long projectId) { }
    public record ApiMessage(String message) { }
}
