"""Bestätigte API-Daten nach sofortigem Abbruch des eigenen Java-Prozesses prüfen."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import uuid

import requests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=str(Path(os.environ['JAVA_HOME'])/'bin'/'java.exe') if os.environ.get('JAVA_HOME') else shutil.which('java'))
    parser.add_argument('--port', type=int, default=8081)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output or root/'tests'/'Ergebnisse'/'dateipersistenz.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    run_dir = output.parent/('Dateipersistenz_'+uuid.uuid4().hex[:8])
    run_dir.mkdir()
    jar = root/'backend'/'target'/'projektverwaltung-1.0.0.jar'
    if not jar.is_file():
        raise FileNotFoundError('Zuerst die Anwendung mit Bauen.ps1 erstellen.')
    # Nie versehentlich einen bereits laufenden fremden Server als Prüfobjekt benutzen.
    try:
        connection = socket.create_connection(('127.0.0.1', args.port), timeout=.2)
    except OSError:
        pass
    else:
        connection.close()
        raise RuntimeError(f'Port {args.port} ist belegt. Einen freien Prüfport wählen.')
    address = f'http://127.0.0.1:{args.port}/api'
    result = {'status': 'LAEUFT', 'beginn': datetime.now().astimezone().isoformat(),
              'pruefung': 'API speichern, ohne Wartezeit eigenen Prozess abbrechen, aus derselben Datei neu laden',
              'datenbank': str(run_dir/'daten.mv.db'), 'quelle_einstellung': 'https://h2database.github.io/html/commands.html#set_write_delay',
              'durchlaeufe': []}
    process = None
    logs = []

    def save():
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')

    def start():
        log = open(run_dir/f'java_{len(logs)+1}.log', 'wb')
        logs.append(log)
        child = subprocess.Popen([args.java, '-jar', str(jar), f'--server.port={args.port}',
                                  '--spring.datasource.url=jdbc:h2:file:'+str(run_dir/'daten').replace('\\','/')],
                                 cwd=run_dir, stdout=log, stderr=subprocess.STDOUT,
                                 creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        for _ in range(150):
            if child.poll() is not None:
                raise RuntimeError('Java-Start fehlgeschlagen; die Datei java_*.log enthält die Ursache. JDK 21 erforderlich.')
            try:
                response = requests.get(address+'/csrf', timeout=.3)
                if response.status_code == 200:
                    return child
            except requests.RequestException:
                pass
            time.sleep(.1)
        child.kill(); child.wait()
        raise RuntimeError('Die eigene Prüf-Anwendung wurde nicht rechtzeitig bereit.')

    def mutate(session, path, data, form=False):
        token_response = session.get(address+'/csrf', timeout=10)
        token_response.raise_for_status(); token = token_response.json()
        body = {'data' if form else 'json': data}
        response = session.post(address+path, headers={token['headerName']: token['token']}, timeout=10, **body)
        response.raise_for_status()
        return response.json()

    def login():
        session = requests.Session()
        mutate(session, '/login', {'username': 'admin', 'password': 'Demo2026!'}, form=True)
        return session

    try:
        save(); process = start()
        for number in range(1, 4):
            session = login(); marker = uuid.uuid4().hex
            project = mutate(session, '/projects', {'name': 'Neustartprüfung '+marker[:8], 'description': marker, 'memberIds': []})
            task = mutate(session, f"/projects/{project['id']}/tasks", {'title': 'Gespeicherte Prüfaufgabe '+marker[:8], 'description': marker, 'status': 'DONE', 'assigneeId': None})
            before = datetime.now().astimezone().isoformat()
            response_time = time.perf_counter()
            previous_pid = process.pid
            process.kill()  # Ausschließlich der von diesem Skript selbst gestartete Prozess.
            kill_delay = (time.perf_counter()-response_time)*1000
            process.wait(timeout=15)
            session.close(); process = start(); session = login()
            response = session.get(address+'/projects', timeout=10); response.raise_for_status()
            loaded_project = next(p for p in response.json() if p['id'] == project['id'])
            response = session.get(address+f"/projects/{project['id']}/tasks", timeout=10); response.raise_for_status()
            loaded_task = next(t for t in response.json() if t['id'] == task['id'])
            assert loaded_project['name'] == project['name'] and loaded_project['description'] == marker
            assert loaded_task['title'] == task['title'] and loaded_task['description'] == marker
            assert loaded_task['status'] == 'DONE' and loaded_project['progress'] == 100
            assert kill_delay < 50, 'Der Test soll ausdrücklich ohne längere Wartezeit abbrechen.'
            evidence = {'nummer': number, 'status': 'BESTANDEN', 'zeit_vorher': before,
                        'zeit_nachher': datetime.now().astimezone().isoformat(), 'abbruch_nach_apiantwort_ms': round(kill_delay,3),
                        'prozess_vorher': previous_pid, 'prozess_nachher': process.pid,
                        'marker': marker, 'projekt': project, 'aufgabe': task,
                        'projekt_nachher': loaded_project, 'aufgabe_nachher': loaded_task}
            result['durchlaeufe'].append(evidence)
            session.close(); save()
            print(f'Durchlauf {number}: BESTANDEN; Abbruchaufruf nach {kill_delay:.3f} ms abgeschlossen.', flush=True)
        result.update(status='BESTANDEN', abgeschlossen=datetime.now().astimezone().isoformat(), wiederholungen=3)
        result.update({key:value for key,value in result['durchlaeufe'][-1].items() if key not in ['nummer','status']})
        result['grenze'] = 'Kein Test eines Stromausfalls, defekter Datenträger oder der Wiederherstellung aus einer Sicherung.'
        save()
    except Exception as error:
        result.update(status='FEHLGESCHLAGEN', fehler=f'{type(error).__name__}: {error}')
        save(); raise
    finally:
        if process is not None and process.poll() is None:
            process.kill(); process.wait(timeout=15)
        for log in logs: log.close()


if __name__ == '__main__':
    main()
