package com.devmod.arena.alert;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ErrorContextTest {

    @Test
    void severityLevels() {
        assertTrue(ErrorContext.Severity.DEBUG.getLevel() < ErrorContext.Severity.INFO.getLevel());
        assertTrue(ErrorContext.Severity.INFO.getLevel() < ErrorContext.Severity.WARNING.getLevel());
        assertTrue(ErrorContext.Severity.WARNING.getLevel() < ErrorContext.Severity.ERROR.getLevel());
        assertTrue(ErrorContext.Severity.ERROR.getLevel() < ErrorContext.Severity.CRITICAL.getLevel());
    }

    @Test
    void severityIsAtLeast() {
        assertTrue(ErrorContext.Severity.CRITICAL.isAtLeast(ErrorContext.Severity.DEBUG));
        assertTrue(ErrorContext.Severity.ERROR.isAtLeast(ErrorContext.Severity.ERROR));
        assertFalse(ErrorContext.Severity.DEBUG.isAtLeast(ErrorContext.Severity.ERROR));
    }

    @Test
    void isCritical() {
        ErrorContext error = createContext(ErrorContext.Severity.ERROR);
        ErrorContext critical = createContext(ErrorContext.Severity.CRITICAL);
        ErrorContext warning = createContext(ErrorContext.Severity.WARNING);

        assertTrue(error.isCritical());
        assertTrue(critical.isCritical());
        assertFalse(warning.isCritical());
    }

    @Test
    void nullValidation() {
        assertThrows(NullPointerException.class, () ->
            new ErrorContext(null, Instant.now(), "type", "msg", List.of(), ErrorContext.Severity.ERROR, "comp", Map.of()));
        assertThrows(NullPointerException.class, () ->
            new ErrorContext(UUID.randomUUID(), null, "type", "msg", List.of(), ErrorContext.Severity.ERROR, "comp", Map.of()));
        assertThrows(NullPointerException.class, () ->
            new ErrorContext(UUID.randomUUID(), Instant.now(), null, "msg", List.of(), ErrorContext.Severity.ERROR, "comp", Map.of()));
        assertThrows(NullPointerException.class, () ->
            new ErrorContext(UUID.randomUUID(), Instant.now(), "type", "msg", List.of(), null, "comp", Map.of()));
    }

    @Test
    void stackFramesTruncatedToMax() {
        List<ErrorContext.StackFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            frames.add(new ErrorContext.StackFrame("Class" + i, "method", "File.java", i, false));
        }
        ErrorContext ctx = new ErrorContext(UUID.randomUUID(), Instant.now(), "type", "msg",
            frames, ErrorContext.Severity.ERROR, "comp", Map.of());
        assertEquals(ErrorContext.MAX_STACK_FRAMES, ctx.stackFrames().size());
    }

    @Test
    void nullStackFramesBecomesEmpty() {
        ErrorContext ctx = new ErrorContext(UUID.randomUUID(), Instant.now(), "type", "msg",
            null, ErrorContext.Severity.ERROR, "comp", Map.of());
        assertNotNull(ctx.stackFrames());
        assertTrue(ctx.stackFrames().isEmpty());
    }

    @Test
    void nullMetadataBecomesEmpty() {
        ErrorContext ctx = new ErrorContext(UUID.randomUUID(), Instant.now(), "type", "msg",
            List.of(), ErrorContext.Severity.ERROR, "comp", null);
        assertNotNull(ctx.metadata());
        assertTrue(ctx.metadata().isEmpty());
    }

    @Test
    void fromThrowable() {
        RuntimeException ex = new RuntimeException("test error");
        ErrorContext ctx = ErrorContext.fromThrowable(ex, ErrorContext.Severity.ERROR, "arena");
        assertEquals("java.lang.RuntimeException", ctx.errorType());
        assertEquals("test error", ctx.message());
        assertEquals("arena", ctx.component());
        assertFalse(ctx.stackFrames().isEmpty());
    }

    @Test
    void stackFrameFromElement() {
        StackTraceElement element = new StackTraceElement("com.Foo", "bar", "Foo.java", 42);
        ErrorContext.StackFrame frame = ErrorContext.StackFrame.from(element);
        assertEquals("com.Foo", frame.className());
        assertEquals("bar", frame.methodName());
        assertEquals("Foo.java", frame.fileName());
        assertEquals(42, frame.lineNumber());
        assertFalse(frame.isNativeMethod());
    }

    @Test
    void stackFrameToMap() {
        ErrorContext.StackFrame frame = new ErrorContext.StackFrame("Cls", "meth", "Cls.java", 10, false);
        Map<String, Object> map = frame.toMap();
        assertEquals("Cls", map.get("className"));
        assertEquals("meth", map.get("methodName"));
        assertEquals(10, map.get("lineNumber"));
    }

    @Test
    void builderCreatesContext() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ErrorContext ctx = ErrorContext.builder()
            .errorId(id)
            .timestamp(now)
            .errorType("TestError")
            .message("msg")
            .severity(ErrorContext.Severity.WARNING)
            .component("test")
            .addMetadata("key1", "val1")
            .build();

        assertEquals(id, ctx.errorId());
        assertEquals(now, ctx.timestamp());
        assertEquals("TestError", ctx.errorType());
        assertEquals(ErrorContext.Severity.WARNING, ctx.severity());
        assertEquals("val1", ctx.metadata().get("key1"));
    }

    @Test
    void builderDefaultsIdAndTimestamp() {
        ErrorContext ctx = ErrorContext.builder()
            .errorType("T")
            .severity(ErrorContext.Severity.INFO)
            .build();
        assertNotNull(ctx.errorId());
        assertNotNull(ctx.timestamp());
    }

    @Test
    void toMapContainsAllFields() {
        ErrorContext ctx = createContext(ErrorContext.Severity.ERROR);
        Map<String, Object> map = ctx.toMap();
        assertTrue(map.containsKey("errorId"));
        assertTrue(map.containsKey("timestamp"));
        assertTrue(map.containsKey("errorType"));
        assertTrue(map.containsKey("severity"));
    }

    private ErrorContext createContext(ErrorContext.Severity severity) {
        return new ErrorContext(UUID.randomUUID(), Instant.now(), "TestError", "msg",
            List.of(), severity, "test", Map.of());
    }
}
