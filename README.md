# Projektverwaltung

Eine lokale Webanwendung für Projekte und Aufgaben eines IT-Dienstleisters. React stellt die Oberfläche dar, Spring Boot verarbeitet die Geschäftsregeln, Spring Data JPA speichert Daten in einer dateibasierten H2-Datenbank. Die Anwendung ist ein Lehrprototyp.

## Starten

Voraussetzungen: JDK 21 oder neuer, Node.js mit npm und beim ersten Build Internetzugang. Der Maven-Wrapper lädt Maven selbst. Unter Windows:

```powershell
.\Bauen.ps1
.\Starten.ps1
```

Danach `http://127.0.0.1:8080` öffnen. Alternativ `Starten.cmd` doppelt anklicken. `Strg+C` beendet den Server. Die Daten bleiben unter `backend/daten` erhalten. Der Server bindet nur an den lokalen Rechner. Die mitgelieferte `.jar` kann aus einem festen Arbeitsordner mit `java -jar projektverwaltung-1.0.0.jar` gestartet werden; dessen Unterordner `daten` enthält dann die Datenbank.

## Demozugänge

Bei einer neuen Datenbank legt die Anwendung fiktive Benutzer an. Das Demo-Passwort lautet **Demo2026!**.

| Konto | Rolle | Bereich |
|---|---|---|
| `admin` | Administration | erster Mandant |
| `leitung` | Projektleitung | erster Mandant |
| `mitarbeiter` | Mitarbeitende | erster Mandant |
| `fremd` | Administration | zweiter Mandant |

Diese bekannten Zugangsdaten dienen dem lokalen Ausprobieren. Für einen echten Betrieb sind eigene Zugangsdaten, HTTPS, sichere Betriebsprozesse und weitere Prüfungen erforderlich. `app.demo-data=false` verhindert die automatische Einrichtung der Demodaten.

## Funktionen

- Administration legt Benutzer an und vergibt Rollen innerhalb des eigenen Mandanten.
- Projektleitungen verwalten ihre zugewiesenen Projekte und ordnen mehrere Mitarbeitende zu. Administration verwaltet Projekte des eigenen Mandanten.
- Projektmitglieder erstellen und bearbeiten Aufgaben mit Verantwortlichem und den Zuständen offen, in Bearbeitung und erledigt.
- Projektfortschritt wird aus dem Anteil erledigter Aufgaben berechnet; ein Projekt ohne Aufgaben hat 0 Prozent Fortschritt.
- Archivierte Projekte bleiben lesbar; Aufgaben darin sind schreibgeschützt.
- Anmeldung und Abmeldung verwenden eine serverseitige Sitzung. Der Server prüft Mandanten- und Projektzugriffe bei jedem fachlichen Zugriff.

## Aufbau und Prüfung

`frontend` enthält React und die vor der Implementierung erstellten SVG-Entwürfe unter `design`. `backend` enthält Controller, Services, JPA-Entitäten und Repositories. `docs` enthält eigene Strukturzeichnungen und die Prüfdokumentation. Die Datenbank hat die Tabellen `tenants`, `app_users`, `projects`, `project_members` und `tasks`.

```powershell
cd backend
.\mvnw.cmd test
```

Für die Frontend-Entwicklung `npm ci` und `npm run dev` in `frontend` ausführen, während das Backend auf Port 8080 läuft. Der Vite-Proxy leitet `/api` weiter. Der fertige Build wird vom Backend selbst ausgeliefert.

## Grenzen

Der Prototyp zeigt die Mandantentrennung auf Anwendungs- und Abfrageebene. Mandanten werden nicht über eine eigene Verwaltungsoberfläche angelegt. Die gemeinsame Datenbank ist keine physische Isolation. Benutzerkennungen sind systemweit eindeutig. Die lokale Einzelinstanz und die H2-Datei wurden nicht auf hohe Last oder verteilten Betrieb ausgelegt. Es gibt keine Passwortzurücksetzung, keine Mehrfaktoranmeldung und keinen externen Identitätsanbieter. Aus den lokalen Funktionstests folgt keine umfassende Sicherheitszertifizierung.

## KI-Unterstützung

Codex unterstützte Entwurf, Implementierung, Tests und Dokumentation. Entscheidungen und Ergebnisse wurden anhand von Quellcode, tatsächlich ausgeführten Prüfungen und Originaldokumentationen kontrolliert. Einzelheiten stehen in `docs/KI_Einsatz.txt`. Es werden ausschließlich eigene Projektdateien veröffentlicht; Hochschulunterlagen und personenbezogene Abgabedokumente gehören nicht zu diesem Repository.

## Browserprüfung wiederholen

Die optionale Browserprüfung verwendet Python mit `playwright` und lokal installiertem Google Chrome unter Windows. Nach `python -m pip install playwright` eine **neue separate Abnahmedatenbank** starten:

```powershell
cd backend
java -jar target/projektverwaltung-1.0.0.jar --spring.datasource.url=jdbc:h2:file:./daten/neue-abnahme
```

In einer zweiten Konsole im Repository `python tests/browserpruefung.py` ausführen. Auf Port 8080 darf dabei keine andere Instanz laufen. Die Prüfung setzt unveränderte Demodaten voraus und verändert fiktive Konten und Projekte. Für einen erneuten Lauf einen neuen Datenbanknamen wählen. Ergebnisse entstehen unter `tests/Bericht`, bestehende Anwendungsdaten werden nicht gelöscht. Die einzelnen Backend-, Browser- und Neustartnachweise sind in `docs/Pruefergebnisse.txt` eingeordnet.

## Diagramme und Dateipersistenz

Die technischen Diagramme liegen als bearbeitbare SVG und hochauflösende PNG unter `docs`. Sie lassen sich mit Python und `cairosvg` durch `python docs/Diagramme_erzeugen.py` erneut erzeugen.

H2 wird beim Verbindungsaufbau mit `SET WRITE_DELAY 0` konfiguriert. So wird das Schreiben des Transaktionsprotokolls nach dem Commit nicht zeitlich aufgeschoben. Der zusätzliche Prozessabbruchtest hat dreimal bestätigt, dass Projekt und Aufgabe auch bei unmittelbarem Beenden des eigenen Java-Prozesses nach der API-Bestätigung wieder verfügbar sind. Das ist kein Stromausfall- oder Backup-Test. Hintergrund der Einstellung: [H2 Commands, SET WRITE_DELAY](https://h2database.github.io/html/commands.html#set_write_delay).

Nach `python -m pip install requests` kann `python tests/dateipersistenz_pruefen.py` den Test wiederholen. Er benötigt JDK 21, die zuvor gebaute JAR und den freien Port 8081. Bei Bedarf `--java` mit dem Pfad zu Java 21 oder `--port` mit einem anderen freien Port angeben. Das Skript startet ausschließlich eigene Prozesse und legt seine isolierte Datenbank und das Ergebnis unter `tests/Ergebnisse` ab. Vorhandene Anwendungsdaten werden nicht benutzt.

Unter Windows vor `Bauen.ps1` einen laufenden Vite-Entwicklungsserver dieses Projekts beenden, damit `npm ci` die nativen Builddateien austauschen kann.
