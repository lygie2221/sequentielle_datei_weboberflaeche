package de.demo.cobol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** One in-memory upload; enforces the limit independently of browser validation. */
public final class UploadSlot {
    private final int limit;
    private ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public UploadSlot(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Limit must be positive");
        this.limit = limit;
    }

    public OutputStream begin() {
        clear();
        ByteArrayOutputStream target = bytes;
        return new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                checkSize(1);
                target.write(value);
            }

            @Override
            public void write(byte[] data, int offset, int length) throws IOException {
                java.util.Objects.checkFromIndexSize(offset, length, data.length);
                checkSize(length);
                target.write(data, offset, length);
            }

            private void checkSize(int count) throws IOException {
                if (count > limit - target.size()) {
                    throw new IOException("Upload exceeds " + limit + " bytes");
                }
            }
        };
    }

    public int size() {
        return bytes.size();
    }

    public byte[] content() {
        return bytes.toByteArray();
    }

    public void clear() {
        bytes = new ByteArrayOutputStream();
    }
}
