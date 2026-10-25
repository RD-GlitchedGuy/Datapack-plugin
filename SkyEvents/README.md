# SkyEvents – Minecraft-Plugin (Paper/Spigot)

## .jar bauen (ohne eigenes Programm)
1. Neues GitHub-Repository anlegen und **alle Dateien/Ordner** dieses Projekts hochladen.
   (Falls der Ordner `.github` beim Hochladen fehlt: *Add file → Create new file*, Name
   `.github/workflows/build.yml`, Inhalt aus der Datei hier reinkopieren.)
2. Tab **Actions** → „Plugin bauen“ → warten bis grüner Haken → unten unter *Artifacts*
   `SkyEvents-jar` herunterladen und entpacken.
3. `SkyEvents-1.0.jar` in den `plugins`-Ordner des Servers, Server neu starten.

Alternativ: Ordner in IntelliJ öffnen → Gradle → `build` → Datei in `build/libs/`.

## Befehle
| Befehl | Wer | Was |
|---|---|---|
| `/elo` | alle | führt `elo.befehle` aus |
| `/elo toggle` | alle | führt `elo.toggle-befehle` aus |
| `/admin` | Admins (OP) | Admin-Menü mit Knöpfen |
| `/admin reload` | Admins | config.yml neu laden |
| `/event` | alle | Menü mit Countdown bis zum nächsten Event |
| `/event settings` | Admins | Event-Zeiten verschieben, sofort starten, zurücksetzen |

## Einstellen
Alles in `plugins/SkyEvents/config.yml`:
- welche Function `/elo`, `/elo toggle` und jeder Admin-Knopf ausführt
- pro Event (Nether, End, beliebig mehr): Zeit, Function, Nachricht, Portal-Sperre
- Scoreboard `time` mit `Tag`, `Monat`, `Jahr`, `Stunde`, `Minute` (echte Zeit)

Rechte: `skyevents.admin` (Standard: OP).
