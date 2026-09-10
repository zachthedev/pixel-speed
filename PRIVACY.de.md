# Datenschutzerklärung

Pixel Speed sammelt keine Daten.

## Was die App liest

Die App liest die Netzwerkzähler des Telefons über Androids
`TrafficStats`-Schnittstelle, berechnet daraus eine Geschwindigkeit und zeichnet
sie auf den Bildschirm. Die Zähler verlassen das Telefon nie.

Ist der Seitenwechsel eingeschaltet, liest ein Bedienungshilfen-Dienst das
Fenster der Statusleiste: welche Symbole sie zeigt und wo sie sitzen. Er liest
kein anderes Fenster und keine Inhalte. Auch das bleibt auf dem Telefon.

Android meldet dem Dienst außerdem, wenn sich Fenster auf dem Bildschirm ändern,
auch Fenster anderer Apps. Dieses Signal sagt ihm nur, wann er wieder auf die
Statusleiste schauen soll. Von einem Fenster am oberen Bildschirmrand liest er
nur, welcher App es gehört, um die Statusleiste zu erkennen. Inhalte liest er
aus keinem anderen Fenster als der Statusleiste.

Die App deklariert keine Netzwerkberechtigung und kann daher nichts irgendwohin
senden. Sie enthält keine Analyse, keinen Dienst für Absturzberichte, keine
Werbung und kein Daten-SDK von Dritten.

## Was die App speichert

Ihre Einstellungen (Profile, Position, Aussehen, Verhalten, Pausenzustand)
liegen in einer Datei im privaten Speicher der App auf dem Telefon. Androids
Sicherung kann diese Datei je nach den Sicherungseinstellungen des Geräts
einschließen. Nichts darin identifiziert Sie.

Der Export schreibt Ihre Einstellungen in eine Datei, die Sie wählen. Der Import
liest eine Datei, die Sie wählen. Beides geschieht nur, wenn Sie es auslösen.

Stürzt die App ab, bewahrt sie einen Bericht dieses Absturzes in ihrem privaten
Speicher auf: den Zeitpunkt, die App-Version, den Stacktrace und die letzten
Protokollzeilen der App selbst. Hilfe > Ein Problem melden erstellt auf Wunsch
eine Diagnosedatei: die App-Version, das Telefonmodell und die Android-Version,
die Display-Geometrie, welche Berechtigungen erteilt sind, den Zustand der
Anzeige, Ihren Einstellungsexport, die letzten Protokollzeilen der App und
diesen Absturzbericht, falls es einen gibt. Sie enthält kein Konto, keine
Geräte-ID, keine Seriennummer und keinen Standort, und nichts aus anderen Apps.
Die App sendet sie nie: Sie lesen sie und teilen sie dann selbst, wenn Sie
möchten.

## Berechtigungen

Jede Berechtigung, die die App deklariert, und wofür sie dient:

- Über anderen Apps anzeigen (SYSTEM_ALERT_WINDOW): zeichnet die Anzeige.
- Benachrichtigungen (POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS): die
  Benachrichtigung des Vordergrunddienstes mit Pause und Fortsetzen, und ihre
  Live-Update-Form ab Android 16.
- Vordergrunddienst (FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE): hält
  die Anzeige am Laufen, solange das Overlay eingeschaltet ist.
- Bedienungshilfen-Dienst (BIND_ACCESSIBILITY_SERVICE): optional, standardmäßig
  aus. Liest die Symbole der Statusleiste und ihre Position, damit die Anzeige
  auf die Seite der Kamera mit Platz wechseln kann. Sonst nichts.
- Netzwerkstatus (ACCESS_NETWORK_STATE): meldet, ob das Telefon online ist und
  welche Verbindung aktiv ist, damit die Anzeige offline Striche zeigt und die
  richtige Schnittstelle zählt. Kein Internetzugriff.
- Beim Start ausführen (RECEIVE_BOOT_COMPLETED): startet das Overlay nach einem
  Neustart, wenn Sie das einschalten.
- Akku-Optimierung ignorieren (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS): wird auf
  Telefonen angeboten, deren Hersteller Hintergrund-Apps beendet, damit das
  Overlay weiterläuft. Optional.

Es gibt keine Netzwerkberechtigung.

## Kontakt

Fragen zu dieser Erklärung: eröffnen Sie ein Issue unter
https://github.com/zachthedev/pixel-speed/issues.

Diese Erklärung gilt für Pixel Speed ab Version 1.0.0. Änderungen werden in
dieser Datei veröffentlicht.
