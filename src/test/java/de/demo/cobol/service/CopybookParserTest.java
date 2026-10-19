package de.demo.cobol.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CopybookParserTest {
    private final CopybookParser parser = new CopybookParser();
    @Test void nestedFieldsHaveQualifiedPathsAndByteOffsets() {
        var layout = parser.parse("01 ROOT.\n 05 GROUP-A.\n  10 NAME PIC X(10).\n  10 AMOUNT PIC S9(3)V99.\n 05 ID PIC 9(2).");
        assertEquals(17, layout.recordLength());
        assertEquals("ROOT.GROUP-A.AMOUNT", layout.fields().get(1).path());
        assertEquals(10, layout.fields().get(1).offset());
        assertEquals(5, layout.fields().get(1).length());
        assertEquals(2, layout.fields().get(1).scale());
        assertTrue(layout.fields().get(1).signed());
        assertEquals(15, layout.fields().get(2).offset());
    }
    @Test void rejectsUnsupportedAmbiguousAndExcessiveLayouts() {
        for (String source : new String[]{"", "01 ROOT PIC X.", "05 NAME PIC X.",
                "01 ROOT.\n05 A PIC 9(4) COMP.", "01 ROOT.\n05 A OCCURS 2 TIMES.",
                "01 ROOT.\n05 A PIC X.\n10 B PIC X.", "01 ROOT.\n05 A PIC X.\n05 A PIC X.",
                "01 ROOT.\n05 A PIC X(0).", "01 ROOT.\n05 A PIC X(999999999999).",
                "01 ROOT.\n05 A PIC X(1001).", "01 ROOT.\n05 EMPTY.\n05 B PIC X.",
                "01 ROOT.\n05 FILLER PIC X.", "01 ROOT.\n05 A PIC 9.\n01 OTHER.\n05 B PIC X."}) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(source), source);
        }
    }
    @Test void fixedFormatCommentsAndSequenceNumbers() {
        var layout = parser.parse("000100 01 ROOT.\n000200* comment\n000300    05 A PIC X(2).\n");
        assertEquals(2, layout.recordLength());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("000100-01 ROOT."));
    }
}
