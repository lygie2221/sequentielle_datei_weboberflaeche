package de.demo.cobol.model;

import java.util.List;

public record RecordLayout(List<FieldLayout> fields, int recordLength) {
    public RecordLayout { fields = List.copyOf(fields); }
}
