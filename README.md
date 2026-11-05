# Projektverwaltung

Lokale Webanwendung für Projekte und Aufgaben eines IT-Dienstleisters. React mit TypeScript stellt die Oberfläche dar, Spring Boot verarbeitet die Geschäftsregeln und Spring Data JPA speichert die Daten in einer H2-Dateidatenbank.

## Starten

Benötigt werden JDK 21 oder neuer und Node.js mit npm. Der erste Build lädt die Abhängigkeiten aus dem Internet; der Maven-Wrapper lädt Maven selbst.

Unter Windows `Starten.cmd` doppelt anklicken oder im Projektordner ausführen:

```powershell
.\Starten.ps1
```

Fehlt die gebaute Anwendung, führt das Startskript den Build aus. Nach Änderungen den Build selbst starten:

```powershell
.\Bauen.ps1
.\Starten.ps1
```

Die Anwendung ist unter **http://127.0.0.1:8080** erreichbar. `Strg+C` beendet sie. Die gespeicherten Daten bleiben in `backend/daten` erhalten. Vor einem neuen Build den laufenden Server und einen eventuell gestarteten Vite-Entwicklungsserver dieses Projekts beenden.

Die fertige JAR lässt sich auch mit `java -jar projektverwaltung-1.0.0.jar` aus einem festen Arbeitsordner starten. Die Datenbank liegt dann in dessen Unterordner `daten`.

## Demozugänge und Funktionen

Eine neue Datenbank erhält vier fiktive Konten mit dem Passwort **Demo2026!**:

| Konto | Rolle | Mandant |
|---|---|---|
| `admin` | Administration | Muster IT GmbH |
| `leitung` | Projektleitung | Muster IT GmbH |
| `mitarbeiter` | Mitarbeitende | Muster IT GmbH |
| `fremd` | Administration | zweiter Mandant |

Die Administration verwaltet Benutzer und Rollen des eigenen Mandanten. Sie und die zuständigen Projektleitungen verwalten Projekte und deren Mitglieder. Projektmitglieder bearbeiten Aufgaben mit Verantwortlichen und dem Status offen, in Bearbeitung oder erledigt. Der Anteil erledigter Aufgaben bestimmt den Fortschritt. Archivierte Projekte bleiben lesbar und sperren Änderungen an Aufgaben.

Der Server prüft Mandanten, Rollen und Projektmitgliedschaften. Anmeldung und Abmeldung verwenden eine Sitzung mit CSRF-Schutz; Passwörter werden mit BCrypt gespeichert.

## Aufbau

- `frontend/src`: Oberfläche, Formulare und API-Aufrufe.
- `backend/src/main`: Controller, Services, Datenmodell, Repositories und Konfiguration.
- `backend/src/test`: Integrationstests für API und Geschäftsregeln.
- `tests`: zusätzliche Browser- und Dateipersistenzprüfungen.

Die Services enthalten die Geschäftsregeln; die Controller nehmen Anfragen entgegen und die Repositories übernehmen den Datenzugriff. Die Datenbank umfasst Mandanten, Benutzer, Projekte, Projektmitgliedschaften und Aufgaben. Entwürfe und Abbildungen sind Bestandteil der separaten Fallstudiendokumentation.

## Prüfen und entwickeln

`Bauen.ps1` baut das Frontend und die JAR und führt die Backendtests aus. Nur die Backendtests starten:

```powershell
cd backend
.\mvnw.cmd test
```

Testumfang und Grenzen stehen in `backend/PRUEFUNG.txt`. Maven erzeugt die Einzelberichte unter `backend/target/surefire-reports`.

Die zusätzlichen Prüfskripte benötigen Python und die Pakete `playwright` und `requests` sowie für den Browsertest installiertes Google Chrome:

```powershell
python -m pip install playwright requests
```

Für den Browsertest zunächst eine separate Anwendung mit **neuen Demodaten** starten. Port 8080 muss frei sein; bei jedem Lauf einen neuen Datenbanknamen wählen:

```powershell
cd backend
java -jar target/projektverwaltung-1.0.0.jar --spring.datasource.url=jdbc:h2:file:./daten/browserpruefung-01
```

Dann in einer zweiten Konsole vom Projektordner aus `python tests/browserpruefung.py` aufrufen. Der Test verändert seine Demodaten. Ergebnisse landen in `tests/Bericht`; mit `--output` lässt sich ein anderer Ordner wählen.

`python tests/dateipersistenz_pruefen.py` prüft die Speicherung über drei sofortige Prozessabbrüche und Neustarts. Das Skript nutzt eigene Datenbanken unter `tests/Ergebnisse` und den freien Port 8081. `--java`, `--port` und `--output` passen die Umgebung an. H2 schreibt das Transaktionsprotokoll durch `SET WRITE_DELAY 0` beim Commit ohne zeitlichen Aufschub.

Für die Frontend-Entwicklung in `frontend` zuerst `npm ci` und dann `npm run dev` ausführen. Das Backend muss auf Port 8080 laufen; Vite leitet `/api` dorthin weiter.

## Einordnung

Die Anwendung ist ein lokaler Lehrprototyp mit bekannten Demozugängen. Für einen produktiven Betrieb wären unter anderem eigene Konten, HTTPS, Backups und weitere Sicherheits- und Lastprüfungen erforderlich. Die gemeinsame H2-Datenbank trennt Mandanten durch die Anwendung, nicht physisch. `app.demo-data=false` schaltet die automatische Einrichtung der Demodaten ab.

Codex unterstützte Entwurf, Implementierung, Tests und Dokumentation. Die Ergebnisse wurden am Quellcode, durch tatsächlich ausgeführte Prüfungen und anhand der Originaldokumentationen kontrolliert. Hochschulunterlagen und personenbezogene Abgabedokumente werden nicht im Repository veröffentlicht.
