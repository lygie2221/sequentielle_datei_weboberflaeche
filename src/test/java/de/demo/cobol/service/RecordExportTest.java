package de.demo.cobol.service;

import de.demo.cobol.model.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static de.demo.cobol.service.RecordCodec.*;

class RecordExportTest {
    private final CopybookParser parser = new CopybookParser();
    private final RecordCodec codec = new RecordCodec();

    @Test void sampleExportChangesOnlyEditedFieldAndCanBeReadAgain() throws Exception {
        var session = new ExplorerSession();
        byte[] original;
        try (var input = getClass().getResourceAsStream("/kunden.cpy")) {
            assertNotNull(input);
            session.loadLayout(input.readAllBytes());
        }
        try (var input = getClass().getResourceAsStream("/kunden.dat")) {
            assertNotNull(input);
            original = input.readAllBytes();
        }
        session.loadData(original, Encoding.LATIN1, Format.LF);
        assertArrayEquals(original, session.exportData());
        var field = session.layout().fields().get(3);
        var row = session.rows().getFirst();
        byte[] raw = row.originalBytes();
        session.edit(row, field, "42,50", Encoding.LATIN1);
        byte[] exported = session.exportData();
        byte[] expected = original.clone();
        System.arraycopy("0004250".getBytes(StandardCharsets.US_ASCII), 0, expected, 30, 7);
        assertArrayEquals(expected, exported);
        assertArrayEquals(raw, row.originalBytes());
        assertEquals(494, exported.length);
        assertEquals("42.50", codec.decode(exported, session.layout(), Encoding.LATIN1, Format.LF)
                .getFirst().value(field.path()));
        session.loadData(original, Encoding.LATIN1, Format.LF);
        assertArrayEquals(original, session.exportData());
        session.clear();
        assertThrows(IllegalArgumentException.class, session::exportData);
    }

    @Test void exportsAllSupportedFormatsAndEncodingsWithOverpunchAndPadding() {
        var layout = parser.parse("01 R.\n05 TXT PIC X(2).\n05 N PIC S9(2)V9.");
        for (Encoding encoding : Encoding.values()) {
            for (Format format : Format.values()) {
                String separator = switch (format) { case FIXED -> ""; case LF -> "\n"; case CRLF -> "\r\n"; };
                byte[] original = ("A 12L" + separator + "B 00}" + separator).getBytes(encoding.charset());
                var rows = codec.decode(original, layout, encoding, format);
                assertArrayEquals(original, codec.encode(rows, layout, encoding, format)); // Negative zero kept intact.
                codec.edit(rows.getFirst(), layout.fields().getFirst(), "Ö ", encoding);
                codec.edit(rows.getFirst(), layout.fields().get(1), "4.5", encoding);
                byte[] exported = codec.encode(rows, layout, encoding, format);
                assertArrayEquals(("Ö 04E" + separator + "B 00}" + separator).getBytes(encoding.charset()), exported);
                assertEquals(original.length, exported.length);
                assertEquals("4.5", codec.decode(exported, layout, encoding, format).getFirst().value(layout.fields().get(1).path()));
                assertArrayEquals(Arrays.copyOfRange(original, 5 + separator.length(), original.length),
                        Arrays.copyOfRange(exported, 5 + separator.length(), exported.length));
            }
        }
    }

    @Test void blocksUnresolvedErrorsAndReencodesRepairedSignedValueEvenIfTextIsIdentical() {
        var layout = parser.parse("01 R.\n05 N PIC S9(3).");
        var row = codec.decode("123".getBytes(), layout, Encoding.LATIN1, Format.FIXED).getFirst();
        assertThrows(IllegalArgumentException.class, () -> codec.encode(java.util.List.of(row), layout, Encoding.LATIN1, Format.FIXED));
        codec.edit(row, layout.fields().getFirst(), "123", Encoding.LATIN1);
        codec.edit(row, layout.fields().getFirst(), "123", Encoding.LATIN1);
        assertTrue(row.edited());
        assertArrayEquals("12C".getBytes(), codec.encode(java.util.List.of(row), layout, Encoding.LATIN1, Format.FIXED));
        assertArrayEquals("123".getBytes(), row.originalBytes());
    }

    @Test void revalidatesEditedValuesAtExportBoundary() {
        var layout = parser.parse("01 R.\n05 TXT PIC X(2).");
        var rows = codec.decode("AB".getBytes(), layout, Encoding.LATIN1, Format.FIXED);
        rows.getFirst().applyValidatedEdit(layout.fields().getFirst().path(), "TOO LONG");
        assertThrows(IllegalArgumentException.class, () -> codec.encode(rows, layout, Encoding.LATIN1, Format.FIXED));
    }
}
