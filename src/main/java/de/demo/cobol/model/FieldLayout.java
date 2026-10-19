package de.demo.cobol.model;

public record FieldLayout(String path, Type type, int offset, int length, int scale, boolean signed) {
    public enum Type { TEXT, NUMERIC }
}
