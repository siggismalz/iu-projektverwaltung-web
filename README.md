# Projektverwaltung

Eine lokale Webanwendung zum Verwalten von Projekten, Mitarbeitenden und Aufgaben. Projekte können angelegt, bearbeitet und archiviert werden. Aufgaben haben Verantwortliche und einen Status; der Projektfortschritt wird automatisch berechnet.

## Voraussetzungen

- Windows mit PowerShell
- Git zum Klonen des Repositorys
- JDK 21 oder neuer
- Node.js 20 ab Version 20.19 oder Node.js ab Version 22.12, jeweils mit npm
- Internetzugang für den ersten Build

`JAVA_HOME` sollte auf den Installationsordner des JDK zeigen. Das Buildskript sucht alternativ in üblichen Installationsordnern. Maven wird automatisch heruntergeladen.

## Starten

1. Repository klonen:

   ```powershell
   git clone https://github.com/siggismalz/iu-projektverwaltung-web.git
   cd iu-projektverwaltung-web
   ```

2. `Starten.cmd` doppelt anklicken. Beim ersten Start wird die Anwendung automatisch gebaut.
3. **http://127.0.0.1:8080** im Browser öffnen.

Mit `Strg+C` im Konsolenfenster wird die Anwendung beendet. Die Daten bleiben im Ordner `backend/daten` gespeichert und werden beim nächsten Start wieder geladen.

## Anmelden

Alle Demokonten haben das Passwort **Demo2026!**.

| Benutzername | Rolle |
|---|---|
| `admin` | Administration: Benutzer, Rollen und Projekte verwalten |
| `leitung` | Projektleitung: eigene Projekte und deren Mitglieder verwalten |
| `mitarbeiter` | Mitarbeitende: zugeordnete Projekte und Aufgaben bearbeiten |
| `fremd` | Administration eines zweiten, getrennten Mandanten |

Für eine kurze Vorführung als `admin` anmelden, ein Projekt öffnen und eine Aufgabe anlegen. Anschließend ihren Status auf „erledigt“ setzen und die Fortschrittsanzeige prüfen. Über „Benutzerverwaltung“ lassen sich Konten und Rollen verwalten.

## Nach Änderungen neu bauen

Zuerst die laufende Anwendung mit `Strg+C` beenden, dann im Projektordner ausführen:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Bauen.ps1
.\Starten.cmd
```

Die Anwendung nutzt React, Spring Boot und eine H2-Dateidatenbank. Sie ist für die lokale Vorführung mit fiktiven Daten und bekannten Demozugängen gedacht.
