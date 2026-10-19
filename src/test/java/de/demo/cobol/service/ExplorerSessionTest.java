package de.demo.cobol.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static de.demo.cobol.service.RecordCodec.*;

class ExplorerSessionTest {
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
