package de.iu.projektverwaltung.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.iu.projektverwaltung.controller.ApiModels.ApiMessage;
import de.iu.projektverwaltung.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.Locale;

/** Richtet die Anmeldung per Sitzung, den CSRF-Schutz und JSON-Fehlermeldungen ein. */
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        // Die Anmeldung vergleicht das eingegebene Passwort mit dem gespeicherten BCrypt-Hash.
        return username -> users.findByUsername(username.toLowerCase(Locale.ROOT))
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Anmeldung fehlgeschlagen."));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        http
            .authorizeHttpRequests(access -> access
                    .requestMatchers("/api/csrf", "/api/login").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
            // Spring prüft bei Änderungen den CSRF-Token aus /api/csrf. Auch die Anmeldung braucht ihn.
            .csrf(Customizer.withDefaults())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form
                    .loginPage("/")
                    .loginProcessingUrl("/api/login")
                    .successHandler((request, response, auth) -> write(json, response, 200, "Angemeldet."))
                    .failureHandler((request, response, error) -> write(json, response, 401, "Benutzername oder Passwort ist falsch.")))
            .logout(logout -> logout
                    .logoutUrl("/api/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, auth) -> write(json, response, 200, "Abgemeldet.")))
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, error) -> write(json, response, 401, "Bitte anmelden."))
                    .accessDeniedHandler((request, response, error) -> write(json, response, 403,
                            "Zugriff verweigert oder Sicherheitstoken abgelaufen. Bitte Seite neu laden.")));
        return http.build();
    }

    private static void write(ObjectMapper json, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), new ApiMessage(message));
    }
}
