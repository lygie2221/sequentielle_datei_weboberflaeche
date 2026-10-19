package de.demo.cobol;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class UploadSlotTest {
    @Test
    void enforcesLimitForChunksAndSingleBytes() throws IOException {
        UploadSlot slot = new UploadSlot(3);
        var stream = slot.begin();
        stream.write(new byte[]{1, 2});
        stream.write(3);
        assertEquals(3, slot.size());
        assertThrows(IOException.class, () -> stream.write(4));
        assertThrows(IOException.class, () -> stream.write(new byte[]{4, 5}));
        assertEquals(3, slot.size());
    }

    @Test
    void replacementAndClearReleasePreviousContents() throws IOException {
        UploadSlot slot = new UploadSlot(3);
        slot.begin().write(new byte[]{1, 2, 3});
        slot.begin().write(4);
        assertEquals(1, slot.size());
        slot.clear();
        assertEquals(0, slot.size());
    }
}
