"""Eigene technische Zeichnungen als scharfe SVG- und PNG-Dateien setzen."""
from pathlib import Path
from html import escape
import cairosvg

DOCS = Path(__file__).resolve().parent


def svg_start(width, height):
    return [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
            '<rect width="100%" height="100%" fill="white"/>',
            '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 Z" fill="#526571"/></marker></defs>']


def text(parts, x, y, value, size=20, weight='normal', anchor='start'):
    parts.append(f'<text x="{x}" y="{y}" font-family="Arial" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="#111111">{escape(value)}</text>')


def path(parts, coords, arrow=False, dashed=False):
    extra = ' marker-end="url(#arrow)"' if arrow else ''
    if dashed: extra += ' stroke-dasharray="7 5"'
    parts.append(f'<path d="{coords}" fill="none" stroke="#526571" stroke-width="2"{extra}/>')


def box(parts, x, y, width, title, rows, operations=None, size=20):
    operations = operations or []
    height = 48 + len(rows)*29 + (17 + len(operations)*29 if operations else 8)
    parts.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" fill="white" stroke="#526571" stroke-width="2"/>')
    parts.append(f'<rect x="{x+1}" y="{y+1}" width="{width-2}" height="42" fill="#EDF2F5"/>')
    path(parts, f'M{x} {y+44} H{x+width}')
    text(parts, x+width/2, y+30, title, 23, 'bold', 'middle')
    for index, row in enumerate(rows): text(parts, x+16, y+73+index*29, row, size)
    if operations:
        divider = y+53+len(rows)*29
        path(parts, f'M{x} {divider} H{x+width}')
        for index, row in enumerate(operations): text(parts, x+16, divider+29+index*29, row, size)
    return height


def save(name, parts):
    parts.append('</svg>')
    svg = DOCS / (name+'.svg')
    png = svg.with_suffix('.png')
    svg.write_text('\n'.join(parts), encoding='utf-8')
    cairosvg.svg2png(url=str(svg), write_to=str(png), scale=2)
    print(name, png.stat().st_size)


# UML-Auszug: Klassen, zentrale Attribute und die tatsächlich implementierten Beziehungen.
p = svg_start(1120, 830)
box(p,410,25,300,'Tenant',['- id: Long','- name: String'])
box(p,40,270,345,'AppUser',['- id: Long','- username: String','- displayName: String','- passwordHash: String','- role: Role'],['+ setRole(role: Role): void'])
box(p,735,270,345,'Project',['- id: Long','- name: String','- description: String','- archived: boolean'],['+ update(...): void'])
box(p,395,585,370,'Task',['- id: Long','- title: String','- description: String','- status: TaskStatus'],['+ update(...): void'])
path(p,'M450 139 L212 270')
text(p,445,183,'1'); text(p,219,252,'0..*')
path(p,'M670 139 L907 270')
text(p,686,183,'1'); text(p,931,252,'0..*')
text(p,560,204,'Mandantenzuordnung',20,anchor='middle')
path(p,'M385 380 H735')
text(p,560,357,'Mitgliedschaft',20,anchor='middle')
text(p,410,407,'0..*'); text(p,681,407,'0..*')
path(p,'M212 509 V695 H395')
text(p,230,553,'0..1'); text(p,350,724,'0..*')
text(p,300,676,'verantwortlich',18,anchor='middle')
path(p,'M907 480 V695 H765')
text(p,925,527,'1'); text(p,784,724,'0..*')
text(p,841,676,'enthält',20,anchor='middle')
save('01_UML_Domaenenmodell',p)

# Horizontale Hauptschichten, Implementierungsklassen als sekundäre Zeilen.
p = svg_start(1200, 420)
def layer(x, title, lines, width=260):
    p.append(f'<rect x="{x}" y="80" width="{width}" height="210" rx="4" fill="#F5F7F8" stroke="#526571" stroke-width="2"/>')
    text(p,x+width/2,120,title,23,'bold','middle')
    for i,line in enumerate(lines):text(p,x+width/2,163+i*31,line,20,anchor='middle')
layer(20,'Präsentation',['React','Ansichten und Formulare','Zustand im Browser'])
layer(420,'Anwendung',['Spring Boot','Controller und Services','Rollen und Fachregeln'],330)
layer(920,'Datenhaltung',['JPA / Hibernate','H2-Dateidatenbank','Dauerhafte Speicherung'])
path(p,'M280 184 H420',True);text(p,350,164,'HTTP / JSON',19,anchor='middle')
path(p,'M750 184 H920',True);text(p,835,164,'Repositories',19,anchor='middle')
text(p,585,335,'Spring Security prüft Anmeldung und CSRF vor den geschützten Endpunkten.',20,anchor='middle')
text(p,585,372,'ApiController → ProjectService / UserService → JPA-Repositories',20,anchor='middle')
save('02_Schichtenarchitektur',p)

# Relationales Schema: Fremdschlüssel zeigen auf den referenzierten Primärschlüssel.
p = svg_start(1200, 815)
box(p,25,225,350,'app_users',['PK  id','FK  tenant_id','username  UNIQUE','display_name','password_hash','role'])
box(p,825,225,350,'projects',['PK  id','FK  tenant_id','name','description','archived'])
box(p,430,20,340,'tenants',['PK  id','name  UNIQUE'])
box(p,430,270,340,'project_members',['FK  project_id','FK  user_id','UNIQUE (project_id, user_id)'])
box(p,430,545,340,'tasks',['PK  id','FK  project_id','FK  assignee_id  optional','title, description','status'])
path(p,'M375 250 L455 134',True);text(p,390,173,'n:1',19,anchor='middle')
path(p,'M825 250 L745 134',True);text(p,812,173,'n:1',19,anchor='middle')
path(p,'M430 345 H375',True);text(p,402,328,'n:1',19,anchor='middle')
path(p,'M770 345 H825',True);text(p,798,328,'n:1',19,anchor='middle')
path(p,'M430 630 L200 455',True);text(p,270,575,'n:0..1',19,anchor='middle')
path(p,'M770 630 L1000 426',True);text(p,938,558,'n:1',19,anchor='middle')
text(p,600,800,'PK = Primärschlüssel     FK = Fremdschlüssel',20,anchor='middle')
save('03_Datenbankschema',p)
