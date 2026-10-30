package trikita.slide.middleware;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class StorageControllerTest {

    private static String read(String content) throws IOException {
        return StorageController.readText(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void dropsTheNewlineAddedOnSave() throws IOException {
        assertEquals("A\n\nB", read("A\n\nB\n"));
    }

    @Test
    public void keepsTextWithoutTrailingNewline() throws IOException {
        assertEquals("A", read("A"));
    }

    @Test
    public void normalizesWindowsLineEndings() throws IOException {
        assertEquals("A\nB", read("A\r\nB\r\n"));
    }

    @Test
    public void readsUtf8() throws IOException {
        assertEquals("スライド ✓", read("スライド ✓\n"));
    }
}
