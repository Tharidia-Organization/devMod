package com.devmod.mailbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MailboxConfigTest {

    private MailboxConfig config;

    @BeforeEach
    void setUp() {
        config = MailboxConfig.INSTANCE;
        // Reset to defaults by re-setting known fields
        config.setMaxMessagesPerPlayer(100);
        config.setMaxSubjectLength(128);
        config.setMaxBodyLength(2000);
        config.setMaxAttachmentsPerMessage(5);
        config.setMaxMessagesPerMinute(10);
        config.setSendCooldownSeconds(5);
        config.setApiPort(8765);
    }

    @Test
    @DisplayName("setMaxMessagesPerPlayer clamps to [10, 500]")
    void setMaxMessagesPerPlayerClamped() {
        config.setMaxMessagesPerPlayer(5);
        assertEquals(10, config.getMaxMessagesPerPlayer());

        config.setMaxMessagesPerPlayer(999);
        assertEquals(500, config.getMaxMessagesPerPlayer());

        config.setMaxMessagesPerPlayer(200);
        assertEquals(200, config.getMaxMessagesPerPlayer());
    }

    @Test
    @DisplayName("setMaxSubjectLength clamps to [32, 256]")
    void setMaxSubjectLengthClamped() {
        config.setMaxSubjectLength(10);
        assertEquals(32, config.getMaxSubjectLength());

        config.setMaxSubjectLength(300);
        assertEquals(256, config.getMaxSubjectLength());
    }

    @Test
    @DisplayName("setMaxBodyLength clamps to [100, 10000]")
    void setMaxBodyLengthClamped() {
        config.setMaxBodyLength(50);
        assertEquals(100, config.getMaxBodyLength());

        config.setMaxBodyLength(20000);
        assertEquals(10000, config.getMaxBodyLength());
    }

    @Test
    @DisplayName("setDefaultMessageTtl clamps to [1, 365] days")
    void setDefaultMessageTtlClamped() {
        config.setDefaultMessageTtl(Duration.ofHours(1));
        assertEquals(1, config.getDefaultMessageTtl().toDays());

        config.setDefaultMessageTtl(Duration.ofDays(500));
        assertEquals(365, config.getDefaultMessageTtl().toDays());
    }

    @Test
    @DisplayName("setMaxAttachmentsPerMessage clamps to [0, 10]")
    void setMaxAttachmentsPerMessageClamped() {
        config.setMaxAttachmentsPerMessage(-1);
        assertEquals(0, config.getMaxAttachmentsPerMessage());

        config.setMaxAttachmentsPerMessage(100);
        assertEquals(10, config.getMaxAttachmentsPerMessage());
    }

    @Test
    @DisplayName("setMaxMessagesPerMinute clamps to [1, 60]")
    void setMaxMessagesPerMinuteClamped() {
        config.setMaxMessagesPerMinute(0);
        assertEquals(1, config.getMaxMessagesPerMinute());

        config.setMaxMessagesPerMinute(100);
        assertEquals(60, config.getMaxMessagesPerMinute());
    }

    @Test
    @DisplayName("setSendCooldownSeconds clamps to [0, 300]")
    void setSendCooldownSecondsClamped() {
        config.setSendCooldownSeconds(-5);
        assertEquals(0, config.getSendCooldownSeconds());

        config.setSendCooldownSeconds(999);
        assertEquals(300, config.getSendCooldownSeconds());
    }

    @Test
    @DisplayName("setApiPort clamps to [1024, 65535]")
    void setApiPortClamped() {
        config.setApiPort(80);
        assertEquals(1024, config.getApiPort());

        config.setApiPort(70000);
        assertEquals(65535, config.getApiPort());
    }

    @Test
    @DisplayName("setContentFilterAction normalizes to BLOCK/FLAG/CENSOR")
    void setContentFilterAction() {
        config.setContentFilterAction("flag");
        assertEquals("FLAG", config.getContentFilterAction());

        config.setContentFilterAction("censor");
        assertEquals("CENSOR", config.getContentFilterAction());

        config.setContentFilterAction("invalid");
        assertEquals("BLOCK", config.getContentFilterAction());

        config.setContentFilterAction(null);
        assertEquals("BLOCK", config.getContentFilterAction());

        config.setContentFilterAction("");
        assertEquals("BLOCK", config.getContentFilterAction());
    }

    @Test
    @DisplayName("setApiAllowedOrigins deduplicates and trims")
    void setApiAllowedOrigins() {
        config.setApiAllowedOrigins(List.of(
            " http://localhost:3000 ",
            "http://localhost:3000",
            "",
            " "
        ));
        List<String> origins = config.getApiAllowedOrigins();
        assertEquals(1, origins.size());
        assertEquals("http://localhost:3000", origins.get(0));
    }

    @Test
    @DisplayName("setApiAllowedOrigins ignores null")
    void setApiAllowedOriginsNull() {
        config.setApiAllowedOrigins(List.of("http://test.com"));
        config.setApiAllowedOrigins(null);
        // Should not crash and keep previous value
        assertFalse(config.getApiAllowedOrigins().isEmpty());
    }

    @Test
    @DisplayName("setApiSecretKey handles null")
    void setApiSecretKeyNull() {
        config.setApiSecretKey(null);
        assertEquals("", config.getApiSecretKey());

        config.setApiSecretKey("secret123");
        assertEquals("secret123", config.getApiSecretKey());
    }

    @Test
    @DisplayName("setDeliveryRetryDelaySeconds bumps max if lower")
    void retryDelayBumpsMax() {
        config.setDeliveryRetryMaxDelaySeconds(10);
        config.setDeliveryRetryDelaySeconds(100);
        assertTrue(config.getDeliveryRetryMaxDelaySeconds() >= config.getDeliveryRetryDelaySeconds());
    }

    @Test
    @DisplayName("setDeliveryRetryBackoffMultiplier clamps NaN to 2.0")
    void backoffMultiplierNaN() {
        config.setDeliveryRetryBackoffMultiplier(Double.NaN);
        assertEquals(2.0, config.getDeliveryRetryBackoffMultiplier(), 0.001);
    }

    @Test
    @DisplayName("setDeliveryRetryJitterRatio clamps to [0.0, 0.5]")
    void jitterRatioClamped() {
        config.setDeliveryRetryJitterRatio(-0.1);
        assertEquals(0.0, config.getDeliveryRetryJitterRatio(), 0.001);

        config.setDeliveryRetryJitterRatio(1.0);
        assertEquals(0.5, config.getDeliveryRetryJitterRatio(), 0.001);
    }

    @Test
    @DisplayName("alias methods delegate correctly")
    void aliasMethods() {
        config.setMaxMessagesPerUser(50);
        assertEquals(50, config.getMaxMessagesPerUser());
        assertEquals(50, config.getMaxMessagesPerPlayer());

        config.setRateLimitPerMinute(20);
        assertEquals(20, config.getRateLimitPerMinute());
        assertEquals(20, config.getMaxMessagesPerMinute());
    }
}
