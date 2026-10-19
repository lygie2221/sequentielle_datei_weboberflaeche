package de.demo.cobol.model;

import java.util.*;

/** Original bytes and values stay immutable; edits and decode errors are separate. */
public final class DataRecord {
    private final int number;
    private final byte[] raw;
    private final Map<String, String> original;
    private final Map<String, String> edits = new HashMap<>();
    private final Map<String, String> errors;
    private final Set<String> invalidOriginal;

    public DataRecord(int number, byte[] raw, Map<String, String> original, Map<String, String> errors) {
        this.number = number;
        this.raw = raw.clone();
        this.original = Map.copyOf(original);
        this.errors = new LinkedHashMap<>(errors);
        this.invalidOriginal = Set.copyOf(errors.keySet());
    }
    public int number() { return number; }
    public byte[] originalBytes() { return raw.clone(); }
    public String value(String path) { return edits.getOrDefault(path, original.get(path)); }
    public String originalValue(String path) { return original.get(path); }
    public Map<String, String> errors() { return Map.copyOf(errors); }
    public boolean edited() { return !edits.isEmpty(); }
    public boolean edited(String path) { return edits.containsKey(path); }
    public void applyValidatedEdit(String path, String value) {
        if (!original.containsKey(path)) throw new IllegalArgumentException("Unbekanntes Feld: " + path);
        if (Objects.equals(original.get(path), value) && !invalidOriginal.contains(path)) edits.remove(path);
        else edits.put(path, value);
        errors.remove(path);
    }
}
