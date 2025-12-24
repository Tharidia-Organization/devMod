package com.devmod.ui.editor.systems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DebugPanelTest {

    @Test
    public void logRecordsEntriesInLIFO() {
        DebugPanel p = new DebugPanel();
        p.log("first");
        p.log("second");
        p.log("third");

        var entries = p.getEntries();
        assertEquals(3, entries.size());
        assertEquals("third", entries.get(0));
        assertEquals("second", entries.get(1));
        assertEquals("first", entries.get(2));
    }

    @Test
    public void exportWritesFileContainingEntries() throws Exception {
        DebugPanel p = new DebugPanel();
        p.log("one");
        p.log("two");
        var path = p.exportLogToTempFile();
        assertTrue(java.nio.file.Files.exists(path));
        String content = java.nio.file.Files.readString(path);
        assertTrue(content.contains("one"));
        assertTrue(content.contains("two"));
        // Cleanup
        java.nio.file.Files.deleteIfExists(path);
    }
}
