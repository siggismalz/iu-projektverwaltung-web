package de.iu.projektverwaltung.service;

import de.iu.projektverwaltung.controller.ApiModels.*;
import de.iu.projektverwaltung.model.AppUser;
import de.iu.projektverwaltung.model.Role;
import de.iu.projektverwaltung.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Verwaltet Konten und prüft die aktuelle Rolle innerhalb eines Mandanten. */
@Service
// Änderungen an geladenen Konten speichert JPA beim Abschluss der Schreibtransaktion.
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public UserService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    /**
     * Mandant und Rolle stammen aus der Datenbank, niemals aus Eingaben des Browsers.
     * Das erneute Laden sorgt dafür, dass Rollenänderungen sofort wirksam werden.
     */
    public AppUser current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Bitte anmelden.");
        }
        return users.findByUsername(auth.getName()).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "Benutzerkonto nicht gefunden. Bitte neu anmelden."));
    }

    public SessionView session(Authentication auth) {
        AppUser user = current(auth);
        return new SessionView(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.getTenant().getName());
    }

    // Ein internes Namensverzeichnis ist nötig, um Aufgaben Projektmitgliedern zuzuweisen.
    public List<UserView> list(Authentication auth) {
        AppUser actor = current(auth);
        return users.findAllByTenantIdOrderByDisplayNameAsc(actor.getTenant().getId()).stream()
                .map(UserService::view).toList();
    }

    @Transactional
    public UserView create(Authentication auth, NewUser input) {
        AppUser actor = current(auth);
        requireAdmin(actor);
        String username = input.username().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Benutzername ist bereits vergeben.");
        }
        // BCrypt verarbeitet höchstens 72 Bytes; Umlaute können mehrere Bytes belegen.
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwort darf höchstens 72 UTF-8-Bytes umfassen.");
        }
        // Gespeichert wird nur der Hash. Den Mandanten bestimmt das angemeldete Konto.
        AppUser user = new AppUser(username, input.displayName().strip(),
                passwords.encode(input.password()), input.role(), actor.getTenant());
        return view(users.save(user));
    }

    @Transactional
    public UserView changeRole(Authentication auth, Long id, RoleChange input) {
        AppUser actor = current(auth);
        requireAdmin(actor);
        AppUser target = users.findByIdAndTenantId(id, actor.getTenant().getId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Benutzerkonto nicht gefunden."));
        // Ohne Administration könnte der Mandant seine Konten nicht mehr verwalten.
        if (target.getRole() == Role.ADMIN && input.role() != Role.ADMIN
                && users.countByTenantIdAndRole(actor.getTenant().getId(), Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Der letzte Administrator des Mandanten muss erhalten bleiben.");
        }
        target.setRole(input.role());
        return view(target);
    }

    public static void requireAdmin(AppUser user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nur die Administration darf Benutzerkonten und Rollen verwalten.");
        }
    }

    public static UserView view(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
