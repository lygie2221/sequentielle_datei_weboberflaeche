package de.demo.cobol.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static de.demo.cobol.service.RecordCodec.*;

class ExplorerSessionTest {
    @Test void addedSampleRecordCanBeEditedExportedAndReloaded() throws Exception {
        var session = new ExplorerSession();
        try (var copybook = getClass().getResourceAsStream("/kunden.cpy");
             var data = getClass().getResourceAsStream("/kunden.dat")) {
            session.loadLayout(copybook.readAllBytes());
            byte[] original = data.readAllBytes();
            session.loadData(original, Encoding.LATIN1, Format.LF);
            var row = session.addRecord();
            assertEquals(14, row.number());
            assertEquals(14, session.rows().size());
            assertTrue(row.errors().isEmpty());
            var name = session.layout().fields().get(1);
            assertEquals(" ".repeat(name.length()), row.value(name.path()));
            assertThrows(IllegalArgumentException.class,
                    () -> session.edit(row, name, "Much too long", Encoding.LATIN1));
            session.edit(row, name, "Dora", Encoding.LATIN1);
            byte[] exported = session.exportData();
            assertArrayEquals(original, java.util.Arrays.copyOf(exported, original.length));
            session.loadData(exported, Encoding.LATIN1, Format.LF);
            assertEquals("Dora      ", session.rows().getLast().value(name.path()));
        }
    }

    @Test void newRecordsRespectEncodingSignsAndFormat() {
        for (var encoding : Encoding.values()) {
            for (var format : Format.values()) {
                var session = new ExplorerSession();
                session.loadLayout("01 R.\n05 A PIC X(2).\n05 N PIC S9(2)V9.".getBytes());
                String separator = switch (format) { case FIXED -> ""; case LF -> "\n"; case CRLF -> "\r\n"; };
                session.loadData(("AB00{" + separator).getBytes(encoding.charset()), encoding, format);
                var row = session.addRecord();
                assertEquals("0.0", row.value("R.N"));
                byte[] bytes = session.exportData();
                session.loadData(bytes, encoding, format);
                assertEquals(2, session.rows().size());
                assertTrue(session.rows().getLast().errors().isEmpty());
                assertEquals("  00{", new String(session.rows().getLast().originalBytes(), encoding.charset()));
            }
        }
    }

    @Test void addingRequiresLoadedDataAndRespectsLimits() {
        var session = new ExplorerSession();
        assertThrows(IllegalArgumentException.class, session::addRecord);
        session.loadLayout("01 R.\n05 A PIC X.".getBytes());
        assertThrows(IllegalArgumentException.class, session::addRecord);
        session.loadData("A".repeat(MAX_RECORDS).getBytes(), Encoding.LATIN1, Format.FIXED);
        assertThrows(IllegalArgumentException.class, session::addRecord);
        assertEquals(MAX_RECORDS, session.rows().size());
        session.clear();
        assertThrows(IllegalArgumentException.class, session::addRecord);
    }

    @Test void reloadAndFailuresNeverExposeStaleRecords() {
        var session = new ExplorerSession();
        session.loadLayout("01 R.\n05 A PIC 9(2).".getBytes());
        session.loadData("12".getBytes(), Encoding.LATIN1, Format.FIXED);
        var field = session.layout().fields().getFirst();
        session.edit(session.rows().getFirst(), field, "34", Encoding.LATIN1);
        assertTrue(session.rows().getFirst().edited());
        session.loadData("12".getBytes(), Encoding.LATIN1, Format.FIXED);
        assertEquals("12", session.rows().getFirst().value(field.path()));
        assertFalse(session.rows().getFirst().edited());
        assertThrows(IllegalArgumentException.class, () -> session.loadData("1".getBytes(), Encoding.LATIN1, Format.FIXED));
        assertTrue(session.rows().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> session.loadLayout(new byte[]{(byte) 0xff}));
        assertNull(session.layout());
        assertTrue(session.rows().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> session.loadData("12".getBytes(), Encoding.LATIN1, Format.FIXED));
    }
}
