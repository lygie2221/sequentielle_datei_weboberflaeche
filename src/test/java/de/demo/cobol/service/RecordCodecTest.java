package de.demo.cobol.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static de.demo.cobol.service.RecordCodec.*;

class RecordCodecTest {
    private final CopybookParser parser = new CopybookParser();
    private final RecordCodec codec = new RecordCodec();
    @Test void sampleFilesDecodeAndEditsPreserveOriginalBytes() throws Exception {
        byte[] data;
        String copybook;
        try (var input = getClass().getResourceAsStream("/kunden.cpy")) {
            assertNotNull(input);
            copybook = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        try (var input = getClass().getResourceAsStream("/kunden.dat")) {
            assertNotNull(input);
            data = input.readAllBytes();
        }
        var layout = parser.parse(copybook);
        var rows = codec.decode(data, layout, Encoding.LATIN1, Format.LF);
        assertEquals(37, layout.recordLength());
        assertEquals(13, rows.size());
        var first = rows.getFirst();
        assertEquals("Anna      ", first.value(layout.fields().get(1).path()));
        var amount = layout.fields().get(3);
        assertEquals("123.45", first.value(amount.path()));
        byte[] raw = first.originalBytes();
        codec.edit(first, amount, "42,50", Encoding.LATIN1);
        assertEquals("42.50", first.value(amount.path()));
        assertEquals("123.45", first.originalValue(amount.path()));
        assertTrue(first.edited());
        assertArrayEquals(raw, first.originalBytes());
        assertThrows(IllegalArgumentException.class, () -> codec.edit(first, amount, "100000.00", Encoding.LATIN1));
        assertEquals("42.50", first.value(amount.path()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(data, layout, Encoding.LATIN1, Format.FIXED));
    }
    @Test void validatesTextAndNumericEditsWithoutSilentPadding() {
        var layout = parser.parse("01 R.\n05 TXT PIC X(2).\n05 N PIC 9(2)V9(2).");
        var text = layout.fields().getFirst();
        assertEquals("A ", codec.validate(text, "A ", Encoding.LATIN1));
        for (String invalid : new String[]{"A", "ABC", "😀", "€ "})
            assertThrows(IllegalArgumentException.class, () -> codec.validate(text, invalid, Encoding.LATIN1));
        var number = layout.fields().get(1);
        assertEquals("1.00", codec.validate(number, "1", Encoding.LATIN1));
        for (String invalid : new String[]{"-1", "+1", "100", "1.001", "1x", "", "1,2.3"})
            assertThrows(IllegalArgumentException.class, () -> codec.validate(number, invalid, Encoding.LATIN1));
    }
    @Test void fixedAndCrlfRecordsAndIncompleteTails() {
        var layout = parser.parse("01 R.\n05 A PIC X(2).");
        assertEquals(2, codec.decode("ABCD".getBytes(), layout, Encoding.LATIN1, Format.FIXED).size());
        assertEquals(2, codec.decode("AB\r\nCD\r\n".getBytes(), layout, Encoding.LATIN1, Format.CRLF).size());
        for (String bad : new String[]{"", "A", "ABC"})
            assertThrows(IllegalArgumentException.class, () -> codec.decode(bad.getBytes(), layout, Encoding.LATIN1, Format.FIXED));
        for (String bad : new String[]{"AB", "AB\r\n", "AB\nC"})
            assertThrows(IllegalArgumentException.class, () -> codec.decode(bad.getBytes(), layout, Encoding.LATIN1, Format.LF));
    }
    @Test void ebcdicSignedOverpunchAndCorruptNumericFields() {
        var layout = parser.parse("01 R.\n05 A PIC S9(2)V9.");
        var field = layout.fields().getFirst();
        var rows = codec.decode("12L12C".getBytes(Encoding.EBCDIC.charset()), layout, Encoding.EBCDIC, Format.FIXED);
        assertEquals("-12.3", rows.getFirst().value(field.path()));
        assertEquals("12.3", rows.get(1).value(field.path()));
        assertEquals("-0.5", codec.validate(field, "-0.5", Encoding.EBCDIC));
        var bad = codec.decode("12?".getBytes(), layout, Encoding.LATIN1, Format.FIXED).getFirst();
        assertFalse(bad.errors().isEmpty());
        codec.edit(bad, field, "1.2", Encoding.LATIN1);
        assertTrue(bad.errors().isEmpty());
        var unsigned = parser.parse("01 R.\n05 A PIC 9(2).");
        assertFalse(codec.decode("A1".getBytes(), unsigned, Encoding.LATIN1, Format.FIXED).getFirst().errors().isEmpty());
    }
    @Test void recordLimitIsEnforced() {
        var layout = parser.parse("01 R.\n05 A PIC X.");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[MAX_RECORDS + 1], layout, Encoding.LATIN1, Format.FIXED));
    }
}
