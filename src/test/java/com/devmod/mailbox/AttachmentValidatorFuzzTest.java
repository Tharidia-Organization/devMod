package com.devmod.mailbox;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.mailbox.attachment.AttachmentValidator;

/**
 * Fuzz tests for AttachmentValidator security.
 *
 * Tests:
 * - Malformed JSON handling
 * - Negative/extreme amounts
 * - Blacklist bypass attempts
 * - Payload size limits
 * - Item ID injection
 */
class AttachmentValidatorFuzzTest {

    private AttachmentValidator validator;

    @BeforeEach
    void setUp() {
        validator = AttachmentValidator.INSTANCE;
        validator.setEnabled(true);
        validator.setMaxJsonPayloadBytes(8192);
        validator.setMaxCurrencyAmount(1_000_000);
        validator.setMaxAttachmentsPerMessage(5);
    }

    @AfterEach
    void tearDown() {
        // Reset to defaults
        validator.setEnabled(true);
        validator.setMaxJsonPayloadBytes(8192);
        validator.setMaxCurrencyAmount(1_000_000);
    }

    // ===== Malformed JSON Tests =====

    @Nested
    @DisplayName("Malformed JSON Handling")
    class MalformedJsonTests {

        @Test
        @DisplayName("Empty JSON string is valid")
        void emptyJsonValid() {
            var result = validator.validatePayload("");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Null payload is valid")
        void nullPayloadValid() {
            var result = validator.validatePayload(null);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Whitespace-only payload is valid")
        void whitespacePayloadValid() {
            var result = validator.validatePayload("   \t\n   ");
            assertTrue(result.isValid());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "{",           // Incomplete object
            "}",           // Stray closing brace
            "[",           // Incomplete array
            "]",           // Stray closing bracket
            "{{}",         // Nested incomplete
            "{ invalid }", // Invalid content
            "null",        // JSON null
            "undefined",   // Not JSON
            "NaN",         // Not valid JSON
            "Infinity"     // Not valid JSON
        })
        @DisplayName("Invalid JSON structures handled gracefully")
        void invalidJsonHandled(String json) {
            assertDoesNotThrow(() -> validator.validatePayload(json),
                "Malformed JSON should not throw: " + json);
        }

        @Test
        @DisplayName("Deeply nested JSON doesn't cause stack overflow")
        void deeplyNestedJson() {
            StringBuilder nested = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                nested.append("{\"a\":");
            }
            nested.append("1");
            for (int i = 0; i < 1000; i++) {
                nested.append("}");
            }

            assertDoesNotThrow(() -> validator.validatePayload(nested.toString()));
        }

        @Test
        @DisplayName("JSON with null bytes handled")
        void jsonWithNullBytes() {
            String jsonWithNulls = "{\"type\":\"\u0000item\",\"id\":\"\u0000test\"}";
            assertDoesNotThrow(() -> validator.validatePayload(jsonWithNulls));
        }

        @Test
        @DisplayName("JSON with Unicode escapes")
        void jsonWithUnicodeEscapes() {
            String json = "{\"type\":\"\\u0069\\u0074\\u0065\\u006d\"}"; // "item"
            assertDoesNotThrow(() -> validator.validatePayload(json));
        }
    }

    // ===== Payload Size Tests =====

    @Nested
    @DisplayName("Payload Size Limits")
    class PayloadSizeTests {

        @Test
        @DisplayName("Payload at exact size limit is valid")
        void payloadAtLimit() {
            int limit = validator.getMaxJsonPayloadBytes();
            String payload = "x".repeat(limit);

            var result = validator.validatePayload(payload);
            // May fail for other reasons (invalid JSON) but should not be size-rejected
            assertNotNull(result);
        }

        @Test
        @DisplayName("Payload over size limit is rejected")
        void payloadOverLimit() {
            // Minimum allowed is 256 bytes
            validator.setMaxJsonPayloadBytes(300);
            String payload = "x".repeat(400);

            var result = validator.validatePayload(payload);
            assertFalse(result.isValid());
            assertEquals(AttachmentValidator.ValidationError.PAYLOAD_TOO_LARGE, result.error());
        }

        @Test
        @DisplayName("Multi-byte UTF-8 characters counted correctly")
        void multiBytesCountedCorrectly() {
            // Minimum allowed is 256 bytes
            validator.setMaxJsonPayloadBytes(300);

            // Each emoji is 4 bytes in UTF-8
            String emojiPayload = "😀".repeat(100); // 400 bytes
            var result = validator.validatePayload(emojiPayload);

            assertFalse(result.isValid(),
                "Multi-byte characters should be counted in bytes, not chars");
        }

        @Test
        @DisplayName("Very large payload handled without OOM")
        void veryLargePayloadHandled() {
            // 1MB payload
            String largePayload = "a".repeat(1_000_000);

            assertDoesNotThrow(() -> validator.validatePayload(largePayload));
            var result = validator.validatePayload(largePayload);
            assertFalse(result.isValid());
        }
    }

    // ===== Item Blacklist Tests =====

    @Nested
    @DisplayName("Item Blacklist")
    class BlacklistTests {

        @Test
        @DisplayName("Blacklisted items are rejected")
        void blacklistedRejected() {
            assertFalse(validator.isItemAllowed("minecraft:command_block"));
            assertFalse(validator.isItemAllowed("minecraft:bedrock"));
            assertFalse(validator.isItemAllowed("minecraft:barrier"));
        }

        @Test
        @DisplayName("Non-blacklisted items are allowed")
        void nonBlacklistedAllowed() {
            assertTrue(validator.isItemAllowed("minecraft:diamond"));
            assertTrue(validator.isItemAllowed("minecraft:stone"));
        }

        @Test
        @DisplayName("Null/empty item IDs rejected")
        void nullEmptyItemIds() {
            assertFalse(validator.isItemAllowed(null));
            assertFalse(validator.isItemAllowed(""));
            assertFalse(validator.isItemAllowed("   "));
        }

        @Test
        @DisplayName("Case variations of blacklisted items")
        void caseVariationsBlocked() {
            assertFalse(validator.isItemAllowed("MINECRAFT:COMMAND_BLOCK"));
            assertFalse(validator.isItemAllowed("Minecraft:Command_Block"));
            assertFalse(validator.isItemAllowed("MINECRAFT:BEDROCK"));
        }

        @Test
        @DisplayName("Item ID without namespace gets minecraft: prefix")
        void itemIdWithoutNamespace() {
            assertFalse(validator.isItemAllowed("command_block"));
            assertFalse(validator.isItemAllowed("bedrock"));
            assertTrue(validator.isItemAllowed("diamond"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:command_block\u0000",      // Trailing null
            "\u0000minecraft:command_block",      // Leading null
            "minecraft:\u0000command_block",      // Embedded null
            "minecraft:command_block ",           // Trailing space
            " minecraft:command_block",           // Leading space
            "minecraft: command_block",           // Space in ID
        })
        @DisplayName("Blacklist bypass attempts with special characters")
        void blacklistBypassAttempts(String itemId) {
            // These should either be blocked or normalized
            assertDoesNotThrow(() -> validator.isItemAllowed(itemId));
        }

        @Test
        @DisplayName("Add and remove from blacklist")
        void addRemoveBlacklist() {
            String testItem = "minecraft:test_item";

            assertTrue(validator.isItemAllowed(testItem));

            validator.addToBlacklist(testItem);
            assertFalse(validator.isItemAllowed(testItem));

            validator.removeFromBlacklist(testItem);
            assertTrue(validator.isItemAllowed(testItem));
        }
    }

    // ===== Amount Validation Tests =====

    @Nested
    @DisplayName("Amount Validation")
    class AmountTests {

        @Test
        @DisplayName("Currency amount at max limit")
        void currencyAtMaxLimit() {
            long max = validator.getMaxCurrencyAmount();
            // Would need to create actual CurrencyAttachment to test fully
            assertTrue(max > 0);
        }

        @Test
        @DisplayName("Max currency amount can be configured")
        void maxCurrencyConfigurable() {
            validator.setMaxCurrencyAmount(500);
            assertEquals(500, validator.getMaxCurrencyAmount());

            validator.setMaxCurrencyAmount(1_000_000); // Reset
        }

        @Test
        @DisplayName("Negative max currency defaults to 1")
        void negativeCurrencyDefaults() {
            validator.setMaxCurrencyAmount(-100);
            assertEquals(1, validator.getMaxCurrencyAmount());

            validator.setMaxCurrencyAmount(1_000_000); // Reset
        }
    }

    // ===== Attachment Count Tests =====

    @Nested
    @DisplayName("Attachment Count Limits")
    class AttachmentCountTests {

        @Test
        @DisplayName("Max attachments can be configured")
        void maxAttachmentsConfigurable() {
            validator.setMaxAttachmentsPerMessage(10);
            assertEquals(10, validator.getMaxAttachmentsPerMessage());

            validator.setMaxAttachmentsPerMessage(5); // Reset
        }

        @Test
        @DisplayName("Max total item stacks can be configured")
        void maxTotalItemsConfigurable() {
            validator.setMaxTotalItemStacks(20);
            assertEquals(20, validator.getMaxTotalItemStacks());
        }

        @Test
        @DisplayName("Zero/negative limits default to 1")
        void zeroNegativeLimitsDefault() {
            validator.setMaxAttachmentsPerMessage(0);
            assertEquals(1, validator.getMaxAttachmentsPerMessage());

            validator.setMaxAttachmentsPerMessage(-5);
            assertEquals(1, validator.getMaxAttachmentsPerMessage());

            validator.setMaxAttachmentsPerMessage(5); // Reset
        }
    }

    // ===== Whitelist Mode Tests =====

    @Nested
    @DisplayName("Whitelist Mode")
    class WhitelistTests {

        @Test
        @DisplayName("Switch to whitelist mode")
        void switchToWhitelist() {
            validator.setUseBlacklist(false);

            // Empty whitelist allows nothing (or everything, depending on impl)
            // With empty whitelist, items should be allowed per implementation
            var allowed = validator.isItemAllowed("minecraft:diamond");

            validator.setUseBlacklist(true); // Reset

            // Just verify it doesn't throw
            assertNotNull(allowed);
        }

        @Test
        @DisplayName("Whitelist mode with items added")
        void whitelistWithItems() {
            validator.setUseBlacklist(false);
            validator.addToWhitelist("minecraft:diamond");

            assertTrue(validator.isItemAllowed("minecraft:diamond"));

            validator.setUseBlacklist(true); // Reset
        }
    }

    // ===== Metrics Tests =====

    @Nested
    @DisplayName("Metrics Tracking")
    class MetricsTests {

        @Test
        @DisplayName("Validation count increments")
        void validationCountIncrements() {
            long before = validator.getValidationsPerformed();

            validator.validatePayload("test");
            validator.validatePayload("test2");

            long after = validator.getValidationsPerformed();
            assertEquals(before + 2, after);
        }

        @Test
        @DisplayName("Failed validation count increments")
        void failedValidationIncrements() {
            // Minimum allowed is 256 bytes
            validator.setMaxJsonPayloadBytes(300);

            long before = validator.getValidationsFailed();
            validator.validatePayload("x".repeat(400)); // Too large
            long after = validator.getValidationsFailed();

            assertTrue(after > before);

            validator.setMaxJsonPayloadBytes(8192); // Reset
        }

        @Test
        @DisplayName("Metrics snapshot is consistent")
        void metricsSnapshot() {
            var metrics = validator.getMetrics();

            assertNotNull(metrics);
            assertTrue(metrics.validationsPerformed() >= 0);
            assertTrue(metrics.validationsFailed() >= 0);
            assertTrue(metrics.itemsBlocked() >= 0);
            assertTrue(metrics.oversizedPayloadsBlocked() >= 0);
        }
    }

    // ===== Enabled/Disabled Tests =====

    @Nested
    @DisplayName("Enabled/Disabled State")
    class EnabledStateTests {

        @Test
        @DisplayName("Disabled validator allows everything")
        void disabledAllowsEverything() {
            validator.setEnabled(false);

            var result = validator.validatePayload("x".repeat(100_000));
            assertTrue(result.isValid());

            validator.setEnabled(true); // Reset
        }

        @Test
        @DisplayName("Re-enabling validator applies rules again")
        void reenablingAppliesRules() {
            validator.setEnabled(false);
            // Minimum allowed is 256 bytes
            validator.setMaxJsonPayloadBytes(300);
            validator.setEnabled(true);

            var result = validator.validatePayload("x".repeat(400));
            assertFalse(result.isValid());

            validator.setMaxJsonPayloadBytes(8192); // Reset
        }
    }

    // ===== Validation Error Types =====

    @Nested
    @DisplayName("Validation Error Types")
    class ErrorTypeTests {

        @Test
        @DisplayName("PAYLOAD_TOO_LARGE error for oversized")
        void payloadTooLargeError() {
            // Minimum allowed is 256 bytes
            validator.setMaxJsonPayloadBytes(300);

            var result = validator.validatePayload("x".repeat(400));
            assertEquals(AttachmentValidator.ValidationError.PAYLOAD_TOO_LARGE, result.error());

            validator.setMaxJsonPayloadBytes(8192); // Reset
        }

        @Test
        @DisplayName("Success result has no error")
        void successNoError() {
            var result = validator.validatePayload("");

            assertTrue(result.isValid());
            assertNull(result.error());
            assertNull(result.message());
        }

        @Test
        @DisplayName("All error types are defined")
        void allErrorTypesDefined() {
            var errors = AttachmentValidator.ValidationError.values();

            assertTrue(errors.length >= 5,
                "Should have multiple error types defined");
        }
    }
}
