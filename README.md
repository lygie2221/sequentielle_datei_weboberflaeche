# COBOL Record Explorer

Lokaler Messeprototyp mit Java 21, Maven, Spring Boot 3.5.6 und Vaadin Flow 24.9.1. Copybook und Datendatei hochladen, Layout prüfen und Datensätze im Grid bearbeiten. Keine Datenbank; Änderungen bleiben bis zum Download im Arbeitsspeicher.

## Voraussetzungen und Start

Java 21 und Maven 3.9.x installieren. Zum ersten Build/Start ist Internetzugriff für Maven-Abhängigkeiten und gegebenenfalls die von Vaadin verwalteten Frontend-Werkzeuge erforderlich.

```sh
mvn clean verify
mvn spring-boot:run
```

Danach http://127.0.0.1:8080 öffnen. Der Server bindet ausschließlich an `127.0.0.1`. Beenden mit Strg+C. Es wird der Vaadin-Entwicklungsmodus verwendet.

`Unable to start embedded Tomcat server` ist eine übergeordnete Fehlermeldung. Bei `Port 8080 was already in use` die bekannte frühere Instanz beenden oder einen anderen Port wählen:

```sh
PORT=8081 mvn spring-boot:run
```

Dann http://127.0.0.1:8081 öffnen. Plattformübergreifende Alternative: `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`. Ohne Angabe bleibt Port 8080 voreingestellt.

## Demonstration

1. Anwendung nach Codeänderungen neu starten und Browserseite neu laden.
2. Unter „COBOL-Copybook“ `src/test/resources/kunden.cpy` hochladen. Das interpretierte Layout wird sofort angezeigt.
3. Unter „Sequenzielle Datendatei“ `src/test/resources/kunden.dat` hochladen. Voreinstellung: **ISO-8859-1**, **Records mit LF (Beispieldatei)**.
4. Das Grid zeigt 13 Datensätze. Über „Bearbeiten“ die Eingabefelder einer Zeile öffnen und beispielsweise den Umsatz auf `42,50` ändern. „Übernehmen“ validiert und speichert im Speicher; „Abbrechen“ verwirft die Eingabe.
5. Zum Prüfen der Fehlerbehandlung einen Umsatz wie `abc` oder `100000` eingeben. Die Zeile bleibt geöffnet und zeigt einen Feldfehler. Bei Textfeldern muss die volle Bytebreite einschließlich Leerzeichen ausdrücklich eingegeben werden, z. B. `Anna      ` für zehn Bytes. Es wird nichts stillschweigend abgeschnitten oder aufgefüllt.

6. Nach „Übernehmen“ auf **„Änderungen als Datei speichern“** klicken. Der Browser lädt `daten-bearbeitet.dat` herunter. Diese Datei kann erneut hochgeladen oder beim Speichern als Ersatz für `kunden.dat` verwendet werden. Kodierung, Recordlängen und gewählte LF-/CRLF-Trenner bleiben erhalten.

Beide Dateien müssen geladen sein, damit Datensätze erscheinen; die Upload-Reihenfolge ist beliebig. Ein neues Upload, Verwerfen einer Datei oder ein Wechsel von Kodierung/Recordformat verwirft Bearbeitungen und lädt die Originaldaten neu. Neuladen der Seite, Sitzungsablauf (30 Minuten Inaktivität) und Prozessende verwerfen ebenfalls die Daten. Das hochgeladene Original auf der Festplatte wird nicht automatisch überschrieben. Zum Ersetzen die heruntergeladene Datei über den Browser/Dateimanager unter dem ursprünglichen Namen speichern. Eine normale Browseranwendung erhält durch den Upload keinen Schreibzugriff auf die Quelldatei.

## Speichern als Datei

Der Speicherknopf erzeugt einen Download des aktuellen, übernommenen Bearbeitungsstands. Bei einer noch geöffneten Editorzeile oder verbleibenden Feldfehlern wird der Download mit einem Hinweis abgelehnt. Nicht übernommene Eingaben werden nicht stillschweigend gespeichert. Bei jedem Klick wird ein neuer Dateistand erzeugt; der Abschluss des Speicherns auf der Festplatte liegt beim Browser.

Nur geänderte Felder werden neu kodiert und an ihre ursprünglichen Byteoffsets geschrieben. Unveränderte Felder bleiben bytegenau erhalten, einschließlich führender Nullen, Leerzeichen und Overpunch. Numerische Werte werden entsprechend PIC mit führenden Nullen und ohne sichtbares Dezimalzeichen geschrieben. Das beim Laden verwendete Recordformat und die Kodierung gelten auch für den Download. Originalbytes und Bearbeitungsstand im Grid werden durch den Download nicht verändert.

## Beispieldateien

`kunden.cpy` verwendet klassisches Quellformat mit freien Spalten 1–7. `kunden.dat` ist US-ASCII-kodiert und damit als ISO-8859-1 lesbar. Sie enthält 13 Records zu je 37 Nutzdatenbytes plus LF, auch nach dem letzten Record: insgesamt 494 Bytes, ohne RDW.

| Feld | Byteoffset (ab 0) | Länge | PIC |
| --- | ---: | ---: | --- |
| KUNDEN-SATZ.KUNDEN-NR | 0 | 5 | 9(5) |
| KUNDEN-SATZ.PERSON.VORNAME | 5 | 10 | X(10) |
| KUNDEN-SATZ.PERSON.NACHNAME | 15 | 15 | X(15) |
| KUNDEN-SATZ.UMSATZ | 30 | 7 | 9(5)V9(2) |

Die ersten drei Records enthalten Anna Schmidt (00001, Umsatz 123,45), Ben Weber (00002, 50,00) und Clara Fischer (00003, 999,99). Zehn weitere Kundensätze ergänzen die Datei bis zur Nummer 00013. Namen sind rechts mit Leerzeichen aufgefüllt. `V` ist ein implizites Dezimalkomma und belegt kein Byte. Die Beispieldatei enthält keine signierten Felder.

## Unterstützte Syntax und Formate

- ASCII-Copybooks ohne BOM, eine Deklaration pro Zeile, genau eine 01-Wurzelgruppe, Level 01–49, verschachtelte benannte Gruppen und eindeutige qualifizierte Feldpfade. Freies oder klassisches Quellformat mit Sequenzspalten 1–6, leerer Spalte 7 und Inhalt bis Spalte 72. Kommentarzeilen mit `*>` bzw. `*` oder `/` in Spalte 7; keine Fortsetzungszeilen oder Inline-Kommentare.
- `PIC`/`PICTURE X`, `X(n)`, `9`, `9(n)`, wiederholte `9`, optionales `S` und `V` mit weiteren Ziffern, z. B. `S9(5)V99`. Kein `USAGE`- oder `SIGN`-Zusatz. Nicht unterstützte Deklarationen werden ausdrücklich abgelehnt.
- DISPLAY-Zahlen mit einer Ziffer pro Byte. Signiertes DISPLAY verwendet ausschließlich nachgestellten Overpunch (kein separates Vorzeichenbyte): positiv `{ABCDEFGHI`, negativ `}JKLMNOPQR` für letzte Ziffer 0–9, z. B. `12L` für −123 ohne Nachkommastellen. Auch positive Werte signierter Felder benötigen Overpunch; andere Vorzeichenkonventionen werden abgelehnt.
- Datendatei wahlweise ISO-8859-1 oder EBCDIC IBM037. Kein UTF-8 wegen variabler Bytebreite. Die Copybook-Kodierung bleibt unabhängig davon ASCII. Eine falsche Kodierung lässt sich bei Textfeldern nicht zuverlässig automatisch erkennen; Auswahl ausdrücklich prüfen.
- Feste Records ohne Trenner oder ausdrücklich ausgewählte LF-/CRLF-Trenner. Jeder Record muss genau die Copybook-Länge haben. Im Zeilenmodus muss auch nach dem letzten Record der gewählte Trenner stehen, kodiert in der gewählten Datendatei-Kodierung. Kein automatisches Raten des Formats. Lesen erfolgt anhand von Byteoffsets, ohne `readLine()`.
- Unvollständige Records und falsche Trenner brechen das Laden mit einer Fehlermeldung ab. Ungültige numerische Felder erscheinen mit Originaltext und Fehlermeldung in der jeweiligen Zeile und können korrigiert werden. Originalbytes und Originalwerte bleiben getrennt von Änderungen erhalten.
- Zahleneingaben erlauben Dezimalkomma oder Punkt, maximal die PIC-Nachkommastellen und bei signierten Feldern ein optionales Vorzeichen. Die Ziffernkapazität und die Kodierbarkeit werden vor Übernahme geprüft. Fehlende führende numerische Nullen ergeben sich aus PIC; Text muss ausdrücklich die exakte Bytebreite erfüllen.

## Demo-Limits und Grenzen

Copybook maximal 256 KiB, Datendatei maximal 10 MiB; Byte-Grenzen zusätzlich serverseitig während des Uploads. HTTP-Multipart-Anfragen maximal 11 MiB. Höchstens 100 elementare Felder, 1000 Bytes pro Feld, 65536 Bytes pro Record, 10000 Records und insgesamt 200000 Zellen. Die Dateien werden vollständig im Speicher gehalten; keine große Dateiverarbeitung oder Pagination. Dateinamen werden nur als Text angezeigt und niemals als Pfad verwendet.

Keine Unterstützung für COMP-3, COMP/BINARY, OCCURS, REDEFINES, FILLER, mehrere Recordlayouts, variable Recordlängen/RDW, COPY-Anweisungen oder andere COBOL-Klauseln. Keine Authentifizierung, Datenbank oder Cloudanbindung. Vaadin-Nutzungsstatistiken sind deaktiviert. Für einen einzelnen lokalen Benutzer und nicht für ein Produktionsdeployment bestimmt.

Parser, Layoutmetadaten, Decoder/Validierung und Sitzungslogik sind unabhängig von Spring/Vaadin. JUnit-Tests prüfen unter anderem verschachtelte Layouts und Offsets, ungültige Syntax, feste und zeilengetrennte Records, EBCDIC, Overpunch, Feldfehler und ungültige Änderungen. Ein Integrationstest verwendet die beiden Beispieldateien und prüft die Erhaltung der Originalbytes bei Bearbeitungen. Exporttests prüfen bytegenaue unveränderte Downloads, einzelne geänderte Felder, erneutes Einlesen sowie feste Records, LF/CRLF, beide Kodierungen und Overpunch.
