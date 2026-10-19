package de.demo.cobol.service;

import de.demo.cobol.model.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.List;

/** Per-view orchestration, independent of Vaadin and Spring. */
public final class ExplorerSession {
    private final CopybookParser parser = new CopybookParser();
    private final RecordCodec codec = new RecordCodec();
    private RecordLayout layout;
    private List<DataRecord> rows = List.of();
    private RecordCodec.Encoding loadedEncoding;
    private RecordCodec.Format loadedFormat;

    public void clear() { layout = null; rows = List.of(); }
    public RecordLayout layout() { return layout; }
    public List<DataRecord> rows() { return rows; }
    public void loadLayout(byte[] bytes) {
        clear();
        try {
            String source = StandardCharsets.US_ASCII.newDecoder().onUnmappableCharacter(CodingErrorAction.REPORT)
                    .onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            layout = parser.parse(source);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Copybook muss ASCII-kodiert sein (nur ASCII-Zeichen, kein BOM).");
        }
    }
    public void loadData(byte[] bytes, RecordCodec.Encoding encoding, RecordCodec.Format format) {
        rows = List.of();
        if (layout == null) throw new IllegalArgumentException("Zuerst ein gültiges Copybook laden.");
        rows = codec.decode(bytes, layout, encoding, format);
        loadedEncoding = encoding;
        loadedFormat = format;
    }
    public byte[] exportData() {
        if (layout == null || rows.isEmpty()) throw new IllegalArgumentException("Keine Datensätze zum Speichern geladen.");
        return codec.encode(rows, layout, loadedEncoding, loadedFormat);
    }
    public String validate(FieldLayout field, String value, RecordCodec.Encoding encoding) {
        return codec.validate(field, value, encoding);
    }
    public void edit(DataRecord row, FieldLayout field, String value, RecordCodec.Encoding encoding) {
        codec.edit(row, field, value, encoding);
    }
}
