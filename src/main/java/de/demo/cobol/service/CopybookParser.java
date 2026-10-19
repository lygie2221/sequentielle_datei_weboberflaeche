package de.demo.cobol.service;

import de.demo.cobol.model.FieldLayout;
import de.demo.cobol.model.RecordLayout;
import java.util.*;
import java.util.regex.*;

/** Strict subset: one declaration per line, free or classic fixed source format. */
public final class CopybookParser {
    private static final Pattern ENTRY = Pattern.compile("^(\\d{2})\\s+([A-Z][A-Z0-9-]*)(?:\\s+PIC(?:TURE)?\\s+([^\\s.]+))?\\s*\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIC = Pattern.compile("^(S)?(9(?:\\(\\d+\\))?)+(?:V(9(?:\\(\\d+\\))?)+)?$|^X(?:\\(\\d+\\))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN = Pattern.compile("([X9])(?:\\((\\d+)\\))?");
    private record Group(int level, String path) {}

    public RecordLayout parse(String source) {
        List<FieldLayout> fields = new ArrayList<>();
        Deque<Group> groups = new ArrayDeque<>();
        Set<String> paths = new HashSet<>();
        int offset = 0, previousLevel = 0, lineNumber = 0;
        boolean previousField = false, rootSeen = false;
        for (String raw : source.split("\\R", -1)) {
            lineNumber++;
            if (raw.length() > 1000) throw error(lineNumber, "Zeile überschreitet 1000 Zeichen.");
            String line = raw;
            // Fixed source: sequence area in columns 1–6; indicator in column 7.
            if (raw.length() >= 7 && raw.substring(0, 6).matches("[ 0-9]{6}")) {
                char indicator = raw.charAt(6);
                if (indicator == '*' || indicator == '/') continue;
                if (indicator != ' ') throw error(lineNumber, "Fortsetzungs-/Indikatorspalte nicht unterstützt.");
                if (raw.length() > 72 && !raw.substring(72).isBlank())
                    throw error(lineNumber, "Inhalt hinter Spalte 72 nicht unterstützt.");
                line = raw.substring(7, Math.min(raw.length(), 72));
            }
            line = line.strip().toUpperCase(Locale.ROOT);
            if (line.isEmpty() || line.startsWith("*>")) continue;
            Matcher entry = ENTRY.matcher(line);
            if (!entry.matches()) throw error(lineNumber, "Nicht unterstützte Deklaration: " + line);
            int level = Integer.parseInt(entry.group(1));
            String name = entry.group(2), picture = entry.group(3);
            if (level < 1 || level > 49 || name.equals("FILLER"))
                throw error(lineNumber, "Nur Level 01–49 und benannte Felder unterstützt.");
            if (!rootSeen) {
                if (level != 1 || picture != null) throw error(lineNumber, "Eine 01-Gruppe als Wurzel erforderlich.");
                rootSeen = true;
            } else if (level == 1) throw error(lineNumber, "Mehrere 01-Records nicht unterstützt.");
            if (previousField && level > previousLevel) throw error(lineNumber, "Elementares Feld kann keine Unterfelder enthalten.");
            while (!groups.isEmpty() && groups.peekLast().level() >= level) groups.removeLast();
            if (level != 1 && groups.isEmpty()) throw error(lineNumber, "Übergeordnete Gruppe fehlt.");
            String path = (groups.isEmpty() ? "" : groups.peekLast().path() + ".") + name;
            if (!paths.add(path)) throw error(lineNumber, "Doppelter Feldpfad: " + path);
            previousLevel = level;
            previousField = picture != null;
            if (picture == null) {
                groups.addLast(new Group(level, path));
            } else {
                if (!PIC.matcher(picture).matches()) throw error(lineNumber, "PIC nicht unterstützt: " + picture);
                boolean text = picture.startsWith("X"), signed = picture.startsWith("S");
                int v = picture.indexOf('V');
                int length = count(picture, lineNumber);
                int scale = v < 0 ? 0 : count(picture.substring(v + 1), lineNumber);
                if (length > 1000 || offset + length > 65536 || fields.size() >= 100)
                    throw error(lineNumber, "Demo-Limit: 100 Felder, 1000 Bytes/Feld, 65536 Bytes/Record.");
                fields.add(new FieldLayout(path, text ? FieldLayout.Type.TEXT : FieldLayout.Type.NUMERIC,
                        offset, length, scale, signed));
                offset += length;
            }
        }
        if (fields.isEmpty()) throw new IllegalArgumentException("Copybook enthält keine elementaren Felder.");
        // Empty groups have no unambiguous layout in this subset.
        for (String path : paths) {
            boolean leaf = fields.stream().anyMatch(f -> f.path().equals(path));
            if (!leaf && fields.stream().noneMatch(f -> f.path().startsWith(path + ".")))
                throw new IllegalArgumentException("Leere Gruppe: " + path);
        }
        return new RecordLayout(fields, offset);
    }

    private int count(String picture, int line) {
        Matcher runs = RUN.matcher(picture);
        long total = 0;
        while (runs.find()) {
            try {
                int n = runs.group(2) == null ? 1 : Integer.parseInt(runs.group(2));
                if (n < 1) throw error(line, "PIC-Wiederholung muss positiv sein.");
                total += n;
                if (total > 1000) throw error(line, "Feld überschreitet 1000 Bytes.");
            } catch (NumberFormatException e) { throw error(line, "PIC-Wiederholung zu groß."); }
        }
        return (int) total;
    }

    private IllegalArgumentException error(int line, String message) {
        return new IllegalArgumentException("Copybook-Zeile " + line + ": " + message);
    }
}
