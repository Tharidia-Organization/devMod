package com.devmod.mailbox;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.mailbox.moderation.ContentFilter;

/**
 * Fuzz tests for ContentFilter security.
 *
 * Tests edge cases and attack vectors:
 * - Boundary strings (empty, max length)
 * - Null bytes and control characters
 * - Word boundary evasion (homoglyphs, zero-width)
 * - ReDoS-susceptible patterns
 * - Unicode normalization bypasses
 */
class ContentFilterFuzzTest {

    private ContentFilter filter;

    @BeforeEach
    void setUp() {
        filter = ContentFilter.INSTANCE;
        filter.setEnabled(true);
        filter.setProhibitedWords(List.of("badword", "spam", "test"));
    }

    @AfterEach
    void tearDown() {
        filter.setProhibitedWords(List.of());
        filter.clearPatterns();
    }

    // ===== Boundary String Tests =====

    @Nested
    @DisplayName("Boundary String Handling")
    class BoundaryStringTests {

        @Test
        @DisplayName("Empty string passes filter")
        void emptyStringPasses() {
            var result = filter.check("");
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("Null string passes filter")
        void nullStringPasses() {
            var result = filter.check(null);
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("Whitespace-only string passes filter")
        void whitespaceOnlyPasses() {
            var result = filter.check("   \t\n   ");
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("Very long string is handled without timeout")
        void veryLongStringHandled() {
            String longString = "a".repeat(100_000);
            long start = System.currentTimeMillis();
            var result = filter.check(longString);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(result.isAllowed());
            assertTrue(elapsed < 5000, "Filter should complete within 5 seconds");
        }

        @Test
        @DisplayName("String at various lengths near boundaries")
        void lengthBoundaryStrings() {
            // Test strings at common boundary sizes
            for (int len : new int[]{1, 255, 256, 1023, 1024, 2047, 2048, 65535, 65536}) {
                String s = "x".repeat(len);
                assertDoesNotThrow(() -> filter.check(s),
                    "Length " + len + " should not throw");
            }
        }
    }

    // ===== Null Bytes and Control Characters =====

    @Nested
    @DisplayName("Control Character Handling")
    class ControlCharacterTests {

        @Test
        @DisplayName("Null bytes in content don't cause issues")
        void nullBytesHandled() {
            String withNulls = "hello\u0000world\u0000test";
            assertDoesNotThrow(() -> filter.check(withNulls));
        }

        @Test
        @DisplayName("All ASCII control characters handled")
        void asciiControlCharsHandled() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                sb.append((char) i);
            }
            sb.append("badword");
            sb.append((char) 127); // DEL

            assertDoesNotThrow(() -> filter.check(sb.toString()));
        }

        @Test
        @DisplayName("Prohibited word with embedded null is not matched")
        void prohibitedWithEmbeddedNull() {
            // Word boundary detection should still work
            // "bad\0word" is NOT the same substring as "badword"
            String content = "hello bad\u0000word here";
            var result = filter.check(content);
            // The null byte breaks the word, so "badword" should NOT match
            // (Note: "test" is in prohibited words, so we use "here" instead)
            assertTrue(result.isAllowed(),
                "Null byte should break word matching - bad\\0word != badword");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "bad\u0000word",    // Null byte
            "bad\u200Bword",   // Zero-width space
            "bad\u200Cword",   // Zero-width non-joiner
            "bad\u200Dword",   // Zero-width joiner
            "bad\uFEFFword"    // Byte order mark
        })
        @DisplayName("Zero-width characters in prohibited words")
        void zeroWidthInProhibitedWord(String content) {
            var result = filter.check(content);
            // These should be handled gracefully (may or may not match based on implementation)
            assertDoesNotThrow(() -> result.isAllowed());
        }
    }

    // ===== Word Boundary Evasion =====

    @Nested
    @DisplayName("Word Boundary Evasion")
    class WordBoundaryEvasionTests {

        @Test
        @DisplayName("Prohibited word with leading/trailing underscore")
        void wordWithUnderscores() {
            var result = filter.check("_badword_");
            // Underscore is not alphanumeric, so word should be detected
            assertFalse(result.isAllowed(),
                "Word surrounded by underscores should be detected");
        }

        @Test
        @DisplayName("Prohibited word embedded in longer word is NOT detected")
        void wordEmbeddedInLongerWord() {
            var result = filter.check("verybadwordly");
            // "badword" inside "verybadwordly" - should NOT match due to word boundaries
            assertTrue(result.isAllowed(),
                "Word embedded in longer word should not match");
        }

        @Test
        @DisplayName("Prohibited word with punctuation boundaries")
        void wordWithPunctuation() {
            var result1 = filter.check("hello,badword!");
            assertFalse(result1.isAllowed(),
                "Word with punctuation boundaries should match");

            var result2 = filter.check("hello.badword?test");
            assertFalse(result2.isAllowed());
        }

        @Test
        @DisplayName("Case variations of prohibited words")
        void caseVariations() {
            assertFalse(filter.check("BADWORD").isAllowed());
            assertFalse(filter.check("BadWord").isAllowed());
            assertFalse(filter.check("bAdWoRd").isAllowed());
        }

        @Test
        @DisplayName("Homoglyph substitution (lookalike characters)")
        void homoglyphSubstitution() {
            // Common homoglyph substitutions that might evade filters
            // These should pass (not detected) unless explicitly handled
            assertTrue(filter.check("b4dword").isAllowed(),
                "Substitution '4' for 'a' should not match 'badword'");
            assertTrue(filter.check("badw0rd").isAllowed(),
                "Substitution '0' for 'o' should not match 'badword'");
            assertTrue(filter.check("b@dword").isAllowed(),
                "Substitution '@' for 'a' should not match 'badword'");
        }
    }

    // ===== ReDoS Prevention =====

    @Nested
    @DisplayName("ReDoS Prevention")
    class ReDoSTests {

        @Test
        @DisplayName("Catastrophic backtracking pattern does not cause timeout")
        void catastrophicBacktrackingPrevented() {
            // Patterns that could cause exponential backtracking
            String evilInput = "a".repeat(30) + "!";

            long start = System.currentTimeMillis();
            assertDoesNotThrow(() -> filter.check(evilInput));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 1000, "Should complete quickly, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("Nested repetition patterns handled")
        void nestedRepetitionHandled() {
            // Input that could cause issues with nested quantifiers
            String nested = "x".repeat(100);

            filter.addProhibitedPattern("(x+)+y");  // Evil pattern

            long start = System.currentTimeMillis();
            assertDoesNotThrow(() -> filter.check(nested));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 2000, "Nested pattern should not cause ReDoS");
        }

        @Test
        @DisplayName("Many alternations pattern handled")
        void manyAlternationsHandled() {
            StringBuilder pattern = new StringBuilder("(");
            for (int i = 0; i < 100; i++) {
                if (i > 0) pattern.append("|");
                pattern.append("word").append(i);
            }
            pattern.append(")");

            filter.addProhibitedPattern(pattern.toString());

            String content = "This is a test message with word50 in it";
            assertDoesNotThrow(() -> filter.check(content));
        }
    }

    // ===== Unicode Normalization =====

    @Nested
    @DisplayName("Unicode Normalization")
    class UnicodeTests {

        @Test
        @DisplayName("Combining characters don't break matching")
        void combiningCharacters() {
            // "a" + combining acute accent = "á" in different forms
            String withCombining = "b\u0061\u0301dword"; // a with combining accent
            assertDoesNotThrow(() -> filter.check(withCombining));
        }

        @Test
        @DisplayName("Fullwidth characters")
        void fullwidthCharacters() {
            // Fullwidth letters: ｂａｄｗｏｒｄ
            String fullwidth = "ｂａｄｗｏｒｄ";
            var result = filter.check(fullwidth);
            // Should pass since filter uses lowercase normalization (not Unicode normalization)
            assertTrue(result.isAllowed(),
                "Fullwidth characters should not match ASCII 'badword'");
        }

        @Test
        @DisplayName("Emoji and symbols don't break matching")
        void emojiHandling() {
            String withEmoji = "hello 😀 badword 🎉 test";
            var result = filter.check(withEmoji);
            assertFalse(result.isAllowed(),
                "badword should be detected even with surrounding emoji");
        }

        @Test
        @DisplayName("RTL and BiDi characters handled")
        void rtlBidiHandling() {
            // Right-to-left override characters
            String rtl = "\u202Ebadword\u202C"; // RLO ... PDF
            assertDoesNotThrow(() -> filter.check(rtl));
        }
    }

    // ===== Censor Function Tests =====

    @Nested
    @DisplayName("Censor Function")
    class CensorTests {

        @Test
        @DisplayName("Censor replaces prohibited word with asterisks")
        void censorReplacesWithAsterisks() {
            String censored = filter.censor("hello badword there");
            assertTrue(censored.contains("*"),
                "Censored output should contain asterisks");
            assertFalse(censored.toLowerCase().contains("badword"),
                "Censored output should not contain prohibited word");
        }

        @Test
        @DisplayName("Censor handles empty/blank input")
        void censorHandlesEmptyInput() {
            assertEquals("", filter.censor(""));
            assertEquals("   ", filter.censor("   "));
        }

        @Test
        @DisplayName("Censor limits asterisk replacement length for long words")
        void censorLimitsReplacementLength() {
            // Clear existing words and add only a very long prohibited word
            filter.setProhibitedWords(List.of());
            filter.addProhibitedWord("x".repeat(100));
            String censored = filter.censor("hello " + "x".repeat(100) + " world");

            // Should not create excessively long asterisk strings (capped at 64)
            long asteriskCount = censored.chars().filter(c -> c == '*').count();
            assertEquals(64, asteriskCount,
                "Asterisk replacement should be capped at 64 for 100-char word");
        }
    }

    // ===== Pattern Validation =====

    @Nested
    @DisplayName("Pattern Validation")
    class PatternValidationTests {

        @Test
        @DisplayName("Invalid regex pattern is rejected gracefully")
        void invalidRegexRejected() {
            // Should not throw, just log warning
            assertDoesNotThrow(() -> filter.addProhibitedPattern("[invalid("));
            assertDoesNotThrow(() -> filter.addProhibitedPattern("*"));
            assertDoesNotThrow(() -> filter.addProhibitedPattern("(?"));
        }

        @Test
        @DisplayName("Null and empty patterns are ignored")
        void nullEmptyPatternsIgnored() {
            assertDoesNotThrow(() -> filter.addProhibitedPattern(null));
            assertDoesNotThrow(() -> filter.addProhibitedPattern(""));
            assertDoesNotThrow(() -> filter.addProhibitedPattern("   "));
        }

        @Test
        @DisplayName("Pattern with special regex characters")
        void specialRegexCharacters() {
            filter.addProhibitedPattern("\\$\\d+\\.\\d{2}");
            var result = filter.check("Price: $10.99");
            assertFalse(result.isAllowed(),
                "Pattern with special chars should match");
        }
    }

    // ===== Message Check =====

    @Nested
    @DisplayName("Message Check (Subject + Body)")
    class MessageCheckTests {

        @Test
        @DisplayName("Prohibited word in subject blocks message")
        void prohibitedInSubject() {
            var result = filter.checkMessage("badword here", "clean body");
            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("Prohibited word in body blocks message")
        void prohibitedInBody() {
            var result = filter.checkMessage("clean subject", "contains badword");
            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("Both null subject and body passes")
        void bothNullPasses() {
            var result = filter.checkMessage(null, null);
            assertTrue(result.isAllowed());
        }
    }
}
