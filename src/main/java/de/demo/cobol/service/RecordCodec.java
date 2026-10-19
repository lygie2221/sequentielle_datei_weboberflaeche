package de.demo.cobol.service;

import de.demo.cobol.model.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public final class RecordCodec {
    public enum Encoding {
        LATIN1("ISO-8859-1"), EBCDIC("IBM037");
        private final Charset charset;
        Encoding(String name) { charset = Charset.forName(name); }
        public Charset charset() { return charset; }
        @Override public String toString() { return charset.name(); }
    }
    public enum Format {
        FIXED("Feste Records ohne Trenner"), LF("Records mit LF (Beispieldatei)"), CRLF("Records mit CRLF");
        private final String label;
        Format(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    private static final String POSITIVE = "{ABCDEFGHI", NEGATIVE = "}JKLMNOPQR";
    public static final int MAX_RECORDS = 10000;
    public static final int MAX_CELLS = 200000;

    public List<DataRecord> decode(byte[] data, RecordLayout layout, Encoding encoding, Format format) {
        if (data.length == 0) throw new IllegalArgumentException("Datendatei ist leer.");
        byte[] separator = separator(encoding, format);
        List<DataRecord> records = new ArrayList<>();
        int pos = 0, length = layout.recordLength();
        while (pos < data.length) {
            if (records.size() >= MAX_RECORDS || (long) (records.size() + 1) * layout.fields().size() > MAX_CELLS)
                throw new IllegalArgumentException("Demo-Limit: höchstens 10000 Records und 200000 Zellen.");
            int number = records.size() + 1;
            if (data.length - pos < length) throw new IllegalArgumentException(
                    "Unvollständiger Record " + number + " bei Byte " + pos + ": " + (data.length - pos) + " statt " + length + " Bytes.");
            byte[] raw = Arrays.copyOfRange(data, pos, pos + length);
            Map<String, String> values = new LinkedHashMap<>(), errors = new LinkedHashMap<>();
            for (FieldLayout field : layout.fields()) {
                String text = new String(raw, field.offset(), field.length(), encoding.charset());
                try { values.put(field.path(), decodeValue(field, text)); }
                catch (IllegalArgumentException e) {
                    values.put(field.path(), text);
                    errors.put(field.path(), e.getMessage());
                }
            }
            records.add(new DataRecord(number, raw, values, errors));
            pos += length;
            if (separator.length != 0) {
                if (data.length - pos < separator.length) throw new IllegalArgumentException("Zeilentrenner nach Record " + number + " fehlt.");
                for (byte expected : separator) {
                    if (data[pos++] != expected) throw new IllegalArgumentException("Falscher Zeilentrenner nach Record " + number + ". Format/Kodierung prüfen.");
                }
            }
        }
        return List.copyOf(records);
    }

    private byte[] separator(Encoding encoding, Format format) {
        return switch (format) {
            case FIXED -> new byte[0];
            case LF -> "\n".getBytes(encoding.charset());
            case CRLF -> "\r\n".getBytes(encoding.charset());
        };
    }

    private String decodeValue(FieldLayout field, String text) {
        if (field.type() == FieldLayout.Type.TEXT) return text; // Preserve original spaces.
        boolean negative = false;
        if (field.signed()) {
            char last = text.charAt(text.length() - 1);
            int digit = POSITIVE.indexOf(last);
            if (digit < 0) { digit = NEGATIVE.indexOf(last); negative = digit >= 0; }
            if (digit < 0) throw new IllegalArgumentException("Signiertes DISPLAY erwartet Overpunch im letzten Byte.");
            text = text.substring(0, text.length() - 1) + digit;
        }
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException("Numerisches DISPLAY enthält ungültige Bytes.");
        BigDecimal number = new BigDecimal(text).movePointLeft(field.scale());
        return (negative ? number.negate() : number).toPlainString();
    }

    /** Validate the encoded width; no implicit padding or truncation of text edits. */
    public String validate(FieldLayout field, String value, Encoding encoding) {
        return validated(field, value, encoding).value();
    }

    private record Validated(String value, byte[] bytes) {}

    private Validated validated(FieldLayout field, String value, Encoding encoding) {
        if (value == null) throw new IllegalArgumentException("Wert fehlt.");
        String encoded = value;
        if (field.type() == FieldLayout.Type.NUMERIC) {
            if (!value.matches((field.signed() ? "[+-]?" : "") + "[0-9]+(?:[.,][0-9]+)?"))
                throw new IllegalArgumentException("Ziffern und optional Dezimalkomma/-punkt" + (field.signed() ? " und Vorzeichen" : " ohne Vorzeichen") + " erwartet.");
            BigDecimal number = new BigDecimal(value.replace(',', '.'));
            if (number.scale() > field.scale()) throw new IllegalArgumentException("Maximal " + field.scale() + " Nachkommastellen.");
            BigDecimal scaled = number.abs().movePointRight(field.scale());
            String digits = scaled.toBigIntegerExact().toString();
            if (digits.length() > field.length()) throw new IllegalArgumentException("Zahl passt nicht in " + field.length() + " Ziffern.");
            encoded = "0".repeat(field.length() - digits.length()) + digits;
            if (field.signed()) {
                int digit = encoded.charAt(encoded.length() - 1) - '0';
                encoded = encoded.substring(0, encoded.length() - 1) + (number.signum() < 0 ? NEGATIVE : POSITIVE).charAt(digit);
            }
            value = number.setScale(field.scale()).toPlainString();
        }
        try {
            ByteBuffer bytes = encoding.charset().newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(encoded));
            if (bytes.remaining() != field.length()) throw new IllegalArgumentException(
                    "Genau " + field.length() + " Bytes erforderlich; aktuell " + bytes.remaining() + ". Leerzeichen bei Text ausdrücklich eingeben.");
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return new Validated(value, result);
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("Zeichen in " + encoding + " nicht darstellbar."); }
    }

    /** Patch only edited fields; untouched bytes (including overpunched zero) stay identical. */
    public byte[] encode(List<DataRecord> records, RecordLayout layout, Encoding encoding, Format format) {
        if (records.isEmpty()) throw new IllegalArgumentException("Keine Datensätze zum Speichern geladen.");
        byte[] separator = separator(encoding, format);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (DataRecord row : records) {
            if (!row.errors().isEmpty()) throw new IllegalArgumentException(
                    "Record " + row.number() + " enthält Feldfehler. Bitte vor dem Speichern korrigieren.");
            byte[] bytes = row.originalBytes();
            if (bytes.length != layout.recordLength()) throw new IllegalArgumentException("Recordlänge passt nicht zum Layout.");
            for (FieldLayout field : layout.fields()) {
                String value = row.value(field.path());
                if (row.edited(field.path())) {
                    byte[] edited = validated(field, value, encoding).bytes();
                    System.arraycopy(edited, 0, bytes, field.offset(), field.length());
                }
            }
            output.writeBytes(bytes);
            output.writeBytes(separator);
        }
        return output.toByteArray();
    }

    public void edit(DataRecord record, FieldLayout field, String value, Encoding encoding) {
        record.applyValidatedEdit(field.path(), validate(field, value, encoding));
    }
}
