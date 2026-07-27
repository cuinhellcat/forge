# Rewind — unser Umbau an Forge

Diese Datei ist die Anleitung, um den Umbau auf eine **neue Forge-Version** zu heben.
Sie beschreibt, was wir geändert haben, warum, und in welcher Reihenfolge man beim
Versionswechsel vorgeht.

Stand: 27.07.2026, Basis Tag `forge-2.0.13`, Zweig `rewind-build`.

---

## 1. Die drei Orte, nicht verwechseln

| Ordner | Was drin ist |
|---|---|
| `~/Programme/MTG-Forge-src` | **Quellcode**, Zweig `rewind-build`. Hier wird entwickelt. |
| `~/Programme/MTG-Forge` | **Installiertes Spiel** — nur die fertigen Programmdateien und `res`. |
| `~/Programme/forge-pr` | Arbeitskopie desselben Repos auf unverändertem Original, je ein Zweig pro Beitrag ans Original-Projekt. Für den Alltag irrelevant. |

Java und Maven liegen portabel daneben:
`~/Programme/jdk-21.0.12+8` und `~/Programme/apache-maven-3.9.11`.

---

## 2. Bauen und installieren

```bash
cd ~/Programme/MTG-Forge-src
export JAVA_HOME=~/Programme/jdk-21.0.12+8
M=~/Programme/apache-maven-3.9.11/bin/mvn

# bauen
$M -o -B -DskipTests install

# testen (299 Tests, dauert ~35 s)
$M -o -B -pl forge-gui-desktop test

# installieren
cp forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar   ~/Programme/MTG-Forge/
cp forge-gui-mobile-dev/target/forge-gui-mobile-dev-*-jar-with-dependencies.jar ~/Programme/MTG-Forge/
cp forge-gui/res/languages/en-US.properties ~/Programme/MTG-Forge/res/languages/
```

**Die Sprachdatei nicht vergessen** — ohne sie stehen im Menü nur die rohen Schlüssel
(`lblRewindToTurn` statt „Restart turn 5").

Fallstricke bei Maven:
- `-pl forge-gui-desktop` allein scheitert, es braucht `-am` dazu.
- `-o` (offline) verhindert, dass Maven beim Bauen im Netz nachlädt.
- `-Dtest=Xyz` braucht zusätzlich `-Dsurefire.failIfNoSpecifiedTests=false`.

---

## 3. Was der Umbau kann

**Rewind** — im Spiel-Menü. Springt an den **Anfang eines deiner eigenen Züge** zurück und
verwirft alles seitdem, auch die Züge der Gegner und der KI. Tiefe 0–10, einstellbar unter
Einstellungen → „Rewind turns" (Standard 3).

Nur der Gastgeber darf zurückspulen. Der Mitspieler braucht **keine** Sonderversion, das
Netzwerkprotokoll ist unverändert; nach dem Rücksprung bekommen alle Clients einen
vollständigen Zustandsabgleich statt der üblichen Differenz.

**Speichern/Laden** — im Spiel-Menü und im Hauptmenü („Load saved game…").

---

## 4. Warum Rewind so gebaut ist, wie es gebaut ist

Der erste Versuch legte bei **jeder Aktion** eine Objektkopie an (`GameSnapshot`). Das ging
schief, immer wieder: Kartenklau kam doppelt zurück, Lebenspunkte stimmten nicht,
Kreaturenschaden fehlte. Grund: `GameSnapshot` überträgt Feld für Feld in lebende Objekte
— jedes vergessene Feld ist ein Fehler, und es gibt Hunderte Felder.

Rewind 2.0 benutzt stattdessen **Forges eigenes Speicherformat** (`GameState`), dasselbe wie
Speichern/Laden und die Puzzles. Das Format wird von den Forge-Entwicklern gepflegt und
kennt Schaden, Kontrolle, Bibliotheken, Stapel, Phase.

Der Preis: Das Format beschreibt **keine** „bis Zugende"-Effekte und keinen laufenden
Kampf. Deshalb entsteht ein Speicherpunkt nur an **einem** Moment im Spiel:

> eigener Zug, erster Hauptzug, Stapel leer, einmal pro Zug

Das ist der einzige Moment, an dem das Format lückenlos ist: Die „bis Zugende"-Effekte der
Vorrunde sind in deren Aufräumphase abgelaufen, kein Kampf läuft, Upkeep und Ziehen sind
verrechnet.

**Diesen Grundsatz nicht aufweichen.** Wer den Speicherpunkt „auch mal mitten im Zug"
erlaubt, holt sich genau die Fehlerklasse zurück, wegen der Rewind 1.0 weggeworfen wurde.

---

## 5. Was wir geändert haben

Sortiert danach, wie viel Arbeit der Wechsel auf eine neue Version macht.

### Gruppe A — unsere Funktion (bleibt immer unsere Arbeit)

Fast alles davon ist **hinzugefügt**, nicht umgeschrieben. Beim Umheben auf eine neue
Version gibt es hier selten echte Konflikte.

| Datei | Was |
|---|---|
| `forge-game/…/Game.java` | Speicherpunkte: `stashTurnRewindPoint`, `rewindToActionOf`, `getAvailableRewindSteps`, `describeRewindPoints`, `clearRewindPoints`; Mitnahme der Command-Zone-Effekte. **Achtung:** `stashGameState`/`restoreGameState` sind wieder wie im Original (eine Kopie). |
| `forge-game/…/phase/PhaseHandler.java` | `PriorityState` (sichert Prioritätsspieler und Phasenzähler, die das Format nicht kennt) und der Einstiegspunkt in `mainLoopStep()`. |
| `forge-game/…/player/PlayerController.java` | `consumeRewindRequest`, `afterRewind`, `setPendingGameState`, `consumePendingGameState`. |
| `forge-gui/…/player/PlayerControllerHuman.java` | Menü-Anfrage, Speichern/Laden, Auto-Pass-Bremse nach dem Rewind. |
| `forge-gui-desktop/…/menus/GameMenu.java` | Rewind-Untermenü, Speichern, Laden. |
| `forge-gui-desktop/…/menus/ForgeMenu.java`, `LoadSavedGame.java` | Spielstand aus dem Hauptmenü starten. |
| Einstellungen (`ForgePreferences`, `VSubmenuPreferences`, `CSubmenuPreferences`, `HostedMatch`) | Die Tiefe als Einstellung. |
| `forge-gui/…/net/server/FServerManager.java` | `resyncAllClients()` nach dem Rücksprung. |
| `forge-gui/res/languages/en-US.properties` | Die Texte. |

### Gruppe B — Fehler in Forge selbst

Diese Änderungen reparieren Forge, nicht unsere Funktion. **Wenn das Original sie
irgendwann selbst behebt, fallen unsere Fassungen weg** — beim Umheben also erst prüfen,
ob die Stelle schon repariert ist.

| Datei | Fehler | Ans Original geschickt? |
|---|---|---|
| `GameSnapshot.java` | Spielsteine/Kopien wurden beim Zurückholen nicht entfernt → Absturz | ja, PR #11416 |
| `GameActionUtil.java` | Abgebrochener Zauber ließ X-Wert, Ziele und den eingefrorenen Stapel zurück | ja, PR #11418 |
| `GameSnapshot.java` | Zone nach Kontrolleur statt Zonen-Besitzer gewählt → Karte im falschen Friedhof | ja, PR #11419 |
| `GameSnapshot.java` | Foretell-Zustand ging verloren (zwei auskommentierte Zeilen) | ja, PR #11420 |
| `GameSnapshot.java` | Leihkontrolle wurde nicht wiederhergestellt; Karte landete in zwei Spielfeldern | nein |
| `GameState.java` | **Spiel laden verlor das Abenteuer einer Karte:** Die Spielerlaubnis entstand beim Einlesen der Exil-Zone und wurde gleich danach beim Aufbau der Command-Zone wieder gelöscht. Jetzt erst am Ende gebaut (`handleAdventures`). | nein |
| `CardFactoryUtil.java` | Der Lader hatte eine **eigene, veraltete Kopie** der Adventure-Definition. Jetzt eine gemeinsame Stelle (`makeAdventureEffect`). | nein |
| `Game.java` (Rewind) | Zustand laden leerte den Stapel nie — was beim Rewind draufliegt, überlebte. Wird jetzt vorher geräumt. Der eigentliche Fehler steckt in `GameState.applyToGame`, wir umgehen ihn nur. | nein |

---

## 6. Die Tests sind der eigentliche Portier-Trick

In `forge-gui-desktop/src/test/java/forge/ai/` liegen unsere Tests. Sie sind der Grund,
warum ein Versionswechsel machbar bleibt: Nach dem Umheben sagt ein Testlauf in 35 Sekunden,
ob der Umbau noch tut, was er soll.

| Test | Prüft |
|---|---|
| `RewindTest` | Speicherpunkte, Zählweise, Tiefe, Zurückspringen, Zug/Phase/Priorität |
| `RewindHandSizeTest` | Maximale Handkartenzahl in beiden Richtungen |
| `RewindLastingEffectTest` | „Für den Rest des Spiels"-Effekte (Praetor's Counsel) |
| `AdventureGameStateTest` | Karte auf Abenteuer bleibt nach dem Laden spielbar |
| `SnapshotStolenCardTest`, `SnapshotControlTest`, `SnapshotMeldTest`, `ForetellSnapshotTest`, `RollbackCleanupTest` | die Fehler aus Gruppe B |

Ein Kniff, der immer wieder gebraucht wird: **Zustände einspielen läuft nur auf dem
Spielfaden.** `GameAction.invoke` erkennt ihn am Thread-Namen. In Tests deshalb:

```java
new Thread(() -> game.rewindToActionOf(p, steps), "Game-test").start();
```

Ohne den Namen passiert scheinbar nichts, und der Test schlägt ohne Fehlermeldung fehl.

---

## 7. Ablauf beim Versionswechsel

1. Neuen Stand holen: `git fetch upstream --tags`
2. **Vorher** die Tests auf dem alten Stand laufen lassen und die Zahl notieren.
3. `git rebase forge-<neue-version> rewind-build`
4. Konflikte abarbeiten. Reihenfolge: erst Gruppe B (kann komplett wegfallen, wenn das
   Original die Stelle selbst repariert hat), dann Gruppe A.
5. Bauen, Tests laufen lassen. Rot heißt: erst reparieren, nicht installieren.
6. Vor dem Kopieren die alten Programmdateien sichern (`*-original.jar.bak` daneben).
7. `forge.profile.properties` muss in den neuen Programmordner — sonst liegen Decks und
   Einstellungen plötzlich woanders.
8. Sprachdatei kopieren.
9. Im Spiel gegenprüfen: eine Partie starten, Rewind-Menü öffnen, einmal zurückspringen.

---

## 8. Bekannte Lücken

- **Merkende Dauereffekte.** Effekte vom Typ „du darfst diese Karte spielen, solange sie im
  Exil ist" (Author of Shadows, Dead Man's Chest, Cruelclaw's Heist) merken sich eine
  bestimmte Karte. Beim Rücksprung baut das Format die Karten neu auf — der Merkzettel
  könnte ins Leere zeigen. Ungetestet, weil keine dieser Karten im Spiel war.
  Lösungsansatz: Karten-Nummern im Speicherpunkt erzwingen und die Merkzettel danach neu
  verknüpfen.
- **Mehrspieler mit echtem Mitspieler** ist nie getestet worden, nur die Mechanik dahinter.
- **Maximale Handkartenzahl** steht nicht im Speicherformat. Beim Rewind egal (sie wird aus
  dem Spielfeld errechnet), beim Laden aus dem Hauptmenü steht in `LoadSavedGame` fest die 7.
- **Melded Karten** werden beim Zurückholen eines `GameSnapshot` zusätzlich lose aufs
  Spielfeld gelegt. Betrifft nur den Abbruch eines Zaubers, nicht Rewind 2.0. Test liegt
  bereit (`SnapshotMeldTest`), nie gemeldet.

---

## 9. Grundsätze, die sich bewährt haben

- **Erst als fehlschlagenden Test nachstellen, dann reparieren.** Zweimal hat das eine
  falsche Vermutung von uns aufgedeckt, bevor sie im Code landete.
- **Nur behaupten, was nachgewiesen ist.** Wenn ein Fehler nicht reproduzierbar ist, gehört
  das dazugesagt, nicht weggelassen.
- **Nicht Feld für Feld nachtragen.** Wenn ein Wert nach dem Rücksprung fehlt, ist die erste
  Frage, ob er sich nicht ohnehin neu berechnen lässt (wie die Handkartenzahl aus dem
  Spielfeld). Erst wenn das nicht geht, wird etwas mitgespeichert.

- **Alles, was beim Laden entstehen soll, entsteht am Ende.** Der Lader räumt erst jede Zone
  leer, baut dann die Karten und **füllt die Zonen erst danach**. Was während des
  Kartenbauens irgendwo abgelegt wird, wischt das Füllen wieder weg.

  Forge ist da schon dreimal hineingelaufen: Kommandeur-Effekt (dort steht der verräterische
  Kommentar *„would have been erased by setCards"*), Geschwindigkeits-Effekt, und die
  Abenteuer-Erlaubnis — letztere war unser Fehler vom 27.07.

  Deshalb läuft auch unser `restoreCommandEffects` **nach** `applyToGame`, nicht davor.
  Faustregel: Wer nach dem Laden etwas in einer Zone haben will, legt es dorthin, wo schon
  `handleCardAttachments` und `handleAdventures` stehen — hinter der Zonen-Schleife.

- **Kopierte Spiellogik läuft auseinander.** Der Lader hatte eine eigene, veraltete Fassung
  der Adventure-Definition: ein fehlendes `Adventure$ True` und ein alter Filter, und schon
  war die Karte nach dem Laden nicht mehr spielbar. Wenn der Lader etwas nachbauen muss, das
  das Spiel auch baut, dann aus **einer gemeinsamen Stelle** (hier
  `CardFactoryUtil.makeAdventureEffect`) — sonst merkt die Kopie eine Regeländerung nie.
