package de.iu.projektverwaltung;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Startpunkt für den Webserver und die Datenbankanbindung. */
@SpringBootApplication
public class ProjektverwaltungApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjektverwaltungApplication.class, args);
    }
}
