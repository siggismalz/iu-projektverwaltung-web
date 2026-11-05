"""Reale Browserabnahme gegen die lokal gestartete Anwendung mit separater Abnahme-Datenbank."""
from pathlib import Path
import json
import sys
from datetime import datetime

ROOT = Path(__file__).resolve().parent
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, str(ROOT / 'werkzeuge'))
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:8080'
OUT = ROOT / 'Bericht'
IMAGES = OUT / 'Abbildungen'
IMAGES.mkdir(parents=True, exist_ok=True)
RESULT = OUT / 'browserpruefung.json'
report = {'zeitpunkt': datetime.now().astimezone().isoformat(), 'basis_url': BASE,
          'browser': 'Google Chrome, headless über Playwright',
          'desktop': {'width': 1440, 'height': 1000}, 'mobil': {'width': 390, 'height': 844},
          'szenarien': [], 'screenshots': [], 'javascript_fehler': [], 'serverfehler_5xx': [],
          'erwartete_http_fehler': [], 'testdaten': {}, 'abgeschlossen': False}


def persist():
    report['erfolgreich'] = sum(x['status'] == 'BESTANDEN' for x in report['szenarien'])
    report['fehlgeschlagen'] = sum(x['status'] == 'FEHLGESCHLAGEN' for x in report['szenarien'])
    RESULT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')


def scenario(name, action):
    try:
        evidence = action()
        report['szenarien'].append({'name': name, 'status': 'BESTANDEN', 'nachweis': evidence})
        print('BESTANDEN:', name, flush=True)
    except Exception as error:
        report['szenarien'].append({'name': name, 'status': 'FEHLGESCHLAGEN', 'fehler': str(error)})
        print('FEHLGESCHLAGEN:', name, str(error), flush=True)
        persist()
        raise
    persist()


def observe(page):
    page.on('pageerror', lambda error: report['javascript_fehler'].append(str(error)))
    def response(res):
        if res.status >= 500:
            report['serverfehler_5xx'].append({'url': res.url, 'status': res.status})
        elif res.status in [401, 403]:
            report['erwartete_http_fehler'].append({'url': res.url, 'status': res.status})
    page.on('response', response)


def screenshot(page, filename, description):
    page.screenshot(path=str(IMAGES / filename), full_page=True, animations='disabled')
    report['screenshots'].append({'datei': str(IMAGES / filename), 'beschreibung': description,
                                  'viewport': page.viewport_size, 'vor_testdatenmutation': True})
    persist()


def login(page, username, password='Demo2026!'):
    page.get_by_label('Benutzername', exact=True).fill(username)
    page.get_by_label('Passwort', exact=True).fill(password)
    page.get_by_role('button', name='Anmelden', exact=True).click()
    expect(page.get_by_role('heading', name='Projekte', exact=True)).to_be_visible()
    expect(page.get_by_text('Daten werden geladen …', exact=True)).to_have_count(0)
    expect(page.get_by_role('link', name='Intranet-Relaunch', exact=True) if username != 'fremd'
           else page.get_by_role('link', name='Fremdes Projekt', exact=True)).to_be_visible()


def get_json(page, path):
    response = page.request.get(BASE + '/api' + path)
    assert response.ok, (path, response.status, response.text())
    return response.json()


def no_overflow(page):
    bounds = page.evaluate('''() => ({viewport:innerWidth,document:document.documentElement.scrollWidth,
      body:document.body.scrollWidth,dialog:document.querySelector('dialog[open]') ? {
      width:document.querySelector('dialog[open]').clientWidth,
      scroll:document.querySelector('dialog[open]').scrollWidth,
      left:document.querySelector('dialog[open]').getBoundingClientRect().left,
      right:document.querySelector('dialog[open]').getBoundingClientRect().right} : null})''')
    assert bounds['document'] <= bounds['viewport'] and bounds['body'] <= bounds['viewport'], bounds
    if bounds['dialog']:
        dialog = bounds['dialog']
        assert dialog['scroll'] <= dialog['width'] and dialog['left'] >= 0 and dialog['right'] <= bounds['viewport'], bounds
    return bounds


def main():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe')
        context = browser.new_context(viewport=report['desktop'], locale='de-DE', reduced_motion='reduce')
        page = context.new_page()
        observe(page)
        page.goto(BASE, wait_until='networkidle')
        expect(page.get_by_role('heading', name='Willkommen zurück')).to_be_visible()
        screenshot(page, '01_Login.png', 'Tatsächliche Anmeldeseite vor der Eingabe.')

        def bad_login():
            page.get_by_label('Benutzername', exact=True).fill('admin')
            page.get_by_label('Passwort', exact=True).fill('AbsichtlichFalsch!')
            with page.expect_response(lambda res: res.url.endswith('/api/login') and res.request.method == 'POST') as response:
                page.get_by_role('button', name='Anmelden', exact=True).click()
            assert response.value.status == 401, response.value.status
            expect(page.get_by_role('alert')).to_be_visible()
            message = page.get_by_role('alert').inner_text()
            assert 'falsch' in message.lower() or 'ungültig' in message.lower(), message
            return {'http_status': 401, 'sichtbare_meldung': message}
        scenario('Ungültige Anmeldung zeigt echte Fehlermeldung', bad_login)

        def good_login():
            login(page, 'admin')
            session = get_json(page, '/session')
            assert session['role'] == 'ADMIN'
            assert len(get_json(page, '/projects')) == 2
            return {'benutzername': session['username'], 'rolle': session['role'], 'sichtbare_projekte': 2}
        scenario('Gültige Anmeldung öffnet Projektübersicht', good_login)
        screenshot(page, '02_Projektuebersicht.png', 'Projektübersicht der Administration mit echten persistenten Demodaten.')

        def navigation():
            page.get_by_role('link', name='Intranet-Relaunch', exact=True).click()
            expect(page.get_by_role('heading', name='Intranet-Relaunch', exact=True)).to_be_visible()
            expect(page.get_by_role('heading', name='Seitennavigation entwerfen', exact=True)).to_be_visible()
            screenshot(page, '03_Projektaufgaben.png', 'Projekt Intranet-Relaunch mit Team, Fortschritt und drei tatsächlichen Aufgaben.')
            page.get_by_role('link', name='← Alle Projekte', exact=True).click()
            expect(page.get_by_role('heading', name='Projekte', exact=True)).to_be_visible()
            page.go_back()
            expect(page.get_by_role('heading', name='Intranet-Relaunch', exact=True)).to_be_visible()
            page.get_by_role('link', name='← Alle Projekte', exact=True).click()
            return {'navigation': 'Übersicht → Detail → Übersicht → Browser-Zurück → Detail → Übersicht'}
        scenario('Seitennavigation und Browser-Zurück', navigation)

        def search():
            field = page.get_by_label('Projekte durchsuchen')
            field.fill('Kundenportal')
            expect(page.get_by_role('link', name='Kundenportal', exact=True)).to_be_visible()
            expect(page.get_by_role('link', name='Intranet-Relaunch', exact=True)).to_have_count(0)
            field.fill('KeinErgebnisBrowsertest')
            expect(page.get_by_role('heading', name='Keine passenden Projekte')).to_be_visible()
            field.fill('')
            expect(page.get_by_role('link', name='Intranet-Relaunch', exact=True)).to_be_visible()
            return {'suche': 'Kundenportal', 'leerzustand': 'Keine passenden Projekte', 'zurueckgesetzt': True}
        scenario('Projektsuche und verständlicher Leerzustand', search)

        def keyboard_dialog():
            trigger = page.get_by_role('button', name='Neues Projekt', exact=True)
            trigger.focus()
            page.keyboard.press('Enter')
            dialog = page.get_by_role('dialog')
            expect(dialog).to_be_visible()
            expect(page.get_by_label('Projektname')).to_be_focused()
            focus_steps = []
            for _ in range(12):
                page.keyboard.press('Tab')
                focus = page.evaluate('''() => { const el=document.activeElement;
                  return {tag:el.tagName,id:el.id,inDialog:document.querySelector('dialog[open]').contains(el),
                  outsideControl:el.matches('button,a,input,select,textarea,[tabindex]') && !document.querySelector('dialog[open]').contains(el)} }''')
                # Native Dialoge dürfen die Browser-Chrome (BODY als activeElement) erreichen;
                # entscheidend ist, dass kein inaktives Steuerelement der Hintergrundseite fokussiert wird.
                assert not focus['outsideControl'], focus
                focus_steps.append(focus)
            screenshot(page, '05_Projektdialog.png', 'Tatsächlicher Dialog zum Anlegen eines Projekts mit Mehrfachauswahl des Teams.')
            page.keyboard.press('Escape')
            expect(dialog).to_have_count(0)
            expect(trigger).to_be_focused()
            return {'oeffnen': 'Enter', 'fokus_anfangs': 'Projektname', 'tab_schritte': focus_steps, 'hintergrund_steuerung_nicht_fokussiert': True, 'schliessen': 'Escape', 'fokus_zurueck': 'Neues Projekt'}
        scenario('Tastaturbedienung, Dialog-Fokus und Escape', keyboard_dialog)

        page.get_by_role('link', name='Benutzerverwaltung', exact=True).click()
        expect(page.get_by_role('heading', name='Benutzerverwaltung', exact=True)).to_be_visible()
        screenshot(page, '04_Benutzerverwaltung.png', 'Benutzerverwaltung mit den drei fiktiven Konten des ersten Mandanten.')
        page.get_by_role('link', name='Projekte', exact=False).first.click()
        expect(page.get_by_role('heading', name='Projekte', exact=True)).to_be_visible()

        def mobile_screens():
            mobile = context.new_page()
            mobile.set_viewport_size(report['mobil'])
            observe(mobile)
            mobile.goto(BASE + '/#projects', wait_until='networkidle')
            expect(mobile.get_by_role('link', name='Intranet-Relaunch', exact=True)).to_be_visible()
            overview = no_overflow(mobile)
            screenshot(mobile, '06_Mobil_Projekte.png', 'Reale mobile Projektübersicht bei 390 × 844 CSS-Pixeln, ohne horizontales Überlaufen.')
            mobile.get_by_role('link', name='Intranet-Relaunch', exact=True).click()
            expect(mobile.get_by_role('heading', name='Seitennavigation entwerfen')).to_be_visible()
            mobile.get_by_role('button', name='Neue Aufgabe', exact=True).click()
            expect(mobile.get_by_role('dialog')).to_be_visible()
            mobile.get_by_label('Aufgabentitel').fill('Abnahme vorbereiten')
            mobile.get_by_label('Beschreibung', exact=True).fill('Die nächsten Schritte mit dem Projektteam abstimmen.')
            mobile.get_by_label('Zuständige Person').select_option(label='Demo Mitarbeit')
            dialog = no_overflow(mobile)
            screenshot(mobile, '07_Mobil_Aufgabe.png', 'Realer mobiler Aufgabendialog mit Eingaben; anschließend ohne Speicherung geschlossen.')
            mobile.keyboard.press('Escape')
            expect(mobile.get_by_role('dialog')).to_have_count(0)
            mobile.close()
            return {'uebersicht': overview, 'aufgabendialog': dialog, 'eingaben_nicht_gespeichert': True}
        scenario('Mobile Darstellung und Aufgabendialog ohne horizontalen Überlauf', mobile_screens)
        print('SCREENSHOTS_BEREIT', str(IMAGES), flush=True)
        persist()

        def create_user():
            page.get_by_role('link', name='Benutzerverwaltung', exact=True).click()
            page.get_by_role('button', name='Benutzerkonto anlegen', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('Anzeigename').fill('Prüfkonto Browsertest')
            dialog.get_by_label('Benutzername', exact=False).fill('browsercheck')
            dialog.get_by_label('Passwort', exact=False).fill('Demo2026!')
            dialog.get_by_label('Rolle', exact=True).select_option('MEMBER')
            dialog.get_by_role('button', name='Benutzerkonto anlegen', exact=True).click()
            expect(dialog).to_have_count(0)
            row = page.get_by_role('row').filter(has_text='browsercheck')
            expect(row).to_contain_text('Mitarbeitende')
            user = next(u for u in get_json(page, '/users') if u['username'] == 'browsercheck')
            report['testdaten']['benutzer'] = user
            return user
        scenario('Benutzerkonto über Oberfläche anlegen', create_user)

        def change_role():
            row = page.get_by_role('row').filter(has_text='browsercheck')
            row.get_by_role('button', name='Rolle für Prüfkonto Browsertest ändern', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('Rolle', exact=True).select_option('LEAD')
            dialog.get_by_role('button', name='Rolle speichern', exact=True).click()
            expect(dialog).to_have_count(0)
            expect(row).to_contain_text('Projektleitung')
            user = next(u for u in get_json(page, '/users') if u['username'] == 'browsercheck')
            assert user['role'] == 'LEAD'
            report['testdaten']['benutzer'] = user
            return {'benutzername': 'browsercheck', 'vorher': 'MEMBER', 'nachher': user['role']}
        scenario('Benutzerrolle über Oberfläche ändern', change_role)

        def create_project():
            page.get_by_role('link', name='Projekte', exact=False).first.click()
            page.get_by_role('button', name='Neues Projekt', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('Projektname').fill('Prüfprojekt Browsertest')
            dialog.get_by_label('Beschreibung', exact=True).fill('Fiktives Projekt zur realen Browserabnahme und späteren Persistenzprüfung.')
            dialog.get_by_role('checkbox', name='Demo Projektleitung leitung').check()
            dialog.get_by_role('checkbox', name='Demo Mitarbeit mitarbeiter').check()
            dialog.get_by_role('button', name='Projekt anlegen', exact=True).click()
            expect(dialog).to_have_count(0)
            page.get_by_role('link', name='Prüfprojekt Browsertest', exact=True).click()
            expect(page.get_by_role('heading', name='Prüfprojekt Browsertest', exact=True)).to_be_visible()
            project = next(p for p in get_json(page, '/projects') if p['name'] == 'Prüfprojekt Browsertest')
            assert len(project['memberIds']) >= 2
            report['testdaten']['projekt'] = project
            return project
        scenario('Projekt mit mehreren Teammitgliedern anlegen', create_project)

        def create_task():
            page.get_by_role('button', name='Neue Aufgabe', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('Aufgabentitel').fill('Browserabnahme durchführen')
            dialog.get_by_label('Beschreibung', exact=True).fill('Navigation, Formulare und Fortschrittsanzeige in der laufenden Anwendung prüfen.')
            dialog.get_by_label('Status', exact=True).select_option('OPEN')
            dialog.get_by_label('Zuständige Person').select_option(label='Demo Mitarbeit')
            dialog.get_by_role('button', name='Aufgabe anlegen', exact=True).click()
            expect(dialog).to_have_count(0)
            expect(page.get_by_role('heading', name='Browserabnahme durchführen')).to_be_visible()
            project = report['testdaten']['projekt']
            task = next(t for t in get_json(page, f"/projects/{project['id']}/tasks") if t['title'] == 'Browserabnahme durchführen')
            assert task['status'] == 'OPEN' and task['assigneeName'] == 'Demo Mitarbeit'
            report['testdaten']['aufgabe'] = task
            return task
        scenario('Aufgabe mit gültiger Teamzuweisung anlegen', create_task)

        def complete_task():
            page.get_by_role('button', name='Aufgabe Browserabnahme durchführen bearbeiten', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('Aufgabentitel').fill('Browserabnahme abgeschlossen')
            dialog.get_by_label('Status', exact=True).select_option('DONE')
            dialog.get_by_role('button', name='Änderungen speichern', exact=True).click()
            expect(dialog).to_have_count(0)
            expect(page.get_by_role('heading', name='Browserabnahme abgeschlossen')).to_be_visible()
            expect(page.get_by_text('100 %', exact=True)).to_be_visible()
            project = next(p for p in get_json(page, '/projects') if p['name'] == 'Prüfprojekt Browsertest')
            assert project['totalTasks'] == 1 and project['doneTasks'] == 1 and project['progress'] == 100
            task = next(t for t in get_json(page, f"/projects/{project['id']}/tasks") if t['title'] == 'Browserabnahme abgeschlossen')
            assert task['status'] == 'DONE'
            report['testdaten']['projekt'] = project
            report['testdaten']['aufgabe'] = task
            return {'geänderter_titel': task['title'], 'status': task['status'], 'gesamt': 1, 'erledigt': 1, 'fortschritt_prozent': 100}
        scenario('Aufgabe bearbeiten und Abschluss im Projektfortschritt nachweisen', complete_task)

        def archive():
            page.get_by_role('button', name='Projekt bearbeiten', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_role('checkbox', name='Projekt archivieren').check()
            dialog.get_by_role('button', name='Änderungen speichern', exact=True).click()
            expect(dialog).to_have_count(0)
            expect(page.get_by_text('Dieses Projekt ist archiviert.', exact=False)).to_be_visible()
            expect(page.get_by_role('button', name='Neue Aufgabe', exact=True)).to_have_count(0)
            expect(page.get_by_role('button', name='Aufgabe Browserabnahme abgeschlossen bearbeiten', exact=True)).to_have_count(0)
            page.get_by_role('link', name='← Alle Projekte', exact=True).click()
            expect(page.get_by_role('link', name='Prüfprojekt Browsertest', exact=True)).to_have_count(0)
            page.get_by_label('Anzeigen', exact=True).select_option('archived')
            expect(page.get_by_role('link', name='Prüfprojekt Browsertest', exact=True)).to_be_visible()
            project = next(p for p in get_json(page, '/projects') if p['name'] == 'Prüfprojekt Browsertest')
            assert project['archived'] is True
            report['testdaten']['projekt'] = project
            return {'archiviert': True, 'aufgabenaktionen_ausgeblendet': True, 'archivfilter': 'Archivierte Projekte'}
        scenario('Archivierung und schreibgeschützte Aufgabenansicht', archive)

        def logout():
            page.get_by_role('button', name='Abmelden', exact=True).click()
            expect(page.get_by_role('heading', name='Willkommen zurück')).to_be_visible()
            response = page.request.get(BASE + '/api/session')
            assert response.status == 401
            return {'anmeldeseite_sichtbar': True, 'session_http_status': response.status}
        scenario('Abmelden beendet die Sitzung', logout)

        def member_access():
            login(page, 'mitarbeiter')
            expect(page.get_by_role('link', name='Benutzerverwaltung', exact=True)).to_have_count(0)
            expect(page.get_by_role('button', name='Neues Projekt', exact=True)).to_have_count(0)
            projects = get_json(page, '/projects')
            names = [p['name'] for p in projects]
            assert 'Intranet-Relaunch' in names and 'Kundenportal' not in names and 'Fremdes Projekt' not in names, names
            page.get_by_role('link', name='Intranet-Relaunch', exact=True).click()
            expect(page.get_by_role('button', name='Neue Aufgabe', exact=True)).to_be_visible()
            expect(page.get_by_role('button', name='Projekt bearbeiten', exact=True)).to_have_count(0)
            page.get_by_role('button', name='Abmelden', exact=True).click()
            expect(page.get_by_role('heading', name='Willkommen zurück')).to_be_visible()
            return {'benutzername': 'mitarbeiter', 'sichtbare_projekte_api': names, 'administration_ausgeblendet': True, 'aufgabenaktion_erreichbar': True}
        scenario('Mitarbeitendenrolle sieht nur zugeordnete Projekte und passende Aktionen', member_access)

        def tenant_access():
            login(page, 'fremd')
            names = [p['name'] for p in get_json(page, '/projects')]
            assert names == ['Fremdes Projekt'], names
            expect(page.get_by_role('link', name='Kundenportal', exact=True)).to_have_count(0)
            expect(page.get_by_role('link', name='Intranet-Relaunch', exact=True)).to_have_count(0)
            page.get_by_role('button', name='Abmelden', exact=True).click()
            expect(page.get_by_role('heading', name='Willkommen zurück')).to_be_visible()
            return {'benutzername': 'fremd', 'sichtbare_projekte': names, 'andere_mandanten_unsichtbar': True}
        scenario('Separater Mandant zeigt ausschließlich eigene Projekte', tenant_access)

        def runtime_quality():
            assert report['javascript_fehler'] == [], report['javascript_fehler']
            assert report['serverfehler_5xx'] == [], report['serverfehler_5xx']
            return {'javascript_pageerror': 0, 'http_5xx': 0, 'erwartete_401_403': len(report['erwartete_http_fehler'])}
        scenario('Keine JavaScript-Laufzeitfehler oder HTTP-5xx im beobachteten Ablauf', runtime_quality)
        report['abgeschlossen'] = True
        persist()
        browser.close()
        print('ABGESCHLOSSEN', report['erfolgreich'], 'bestanden;', report['fehlgeschlagen'], 'fehlgeschlagen', flush=True)


if __name__ == '__main__':
    try:
        main()
    finally:
        persist()
