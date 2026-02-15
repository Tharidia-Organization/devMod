package com.devmod.mailbox.attachment;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyAttachmentTest {

    @AfterEach
    void tearDown() {
        CurrencyAttachment.clearCurrencyRules();
    }

    @Test
    @DisplayName("tokens factory creates correct attachment")
    void tokensFactory() {
        CurrencyAttachment attachment = CurrencyAttachment.tokens(100);
        assertEquals("tokens", attachment.currencyType());
        assertEquals(100, attachment.amount());
        assertEquals(CurrencyAttachment.TYPE, attachment.getType());
    }

    @Test
    @DisplayName("coins factory creates correct attachment")
    void coinsFactory() {
        CurrencyAttachment attachment = CurrencyAttachment.coins(50);
        assertEquals("coins", attachment.currencyType());
        assertEquals(50, attachment.amount());
    }

    @Test
    @DisplayName("gems factory creates correct attachment")
    void gemsFactory() {
        CurrencyAttachment attachment = CurrencyAttachment.gems(25);
        assertEquals("gems", attachment.currencyType());
        assertEquals(25, attachment.amount());
    }

    @Test
    @DisplayName("getDescription shows formatted text")
    void getDescription() {
        assertEquals("100 Tokens", CurrencyAttachment.tokens(100).getDescription());
        assertEquals("50 Coins", CurrencyAttachment.coins(50).getDescription());
        assertEquals("25 Gems", CurrencyAttachment.gems(25).getDescription());
    }

    @Test
    @DisplayName("validate rejects null/blank currency type")
    void validateNullCurrency() {
        CurrencyAttachment attachment = new CurrencyAttachment(null, 10);
        assertNotNull(attachment.validate());
        assertFalse(attachment.isValid());

        CurrencyAttachment blank = new CurrencyAttachment("", 10);
        assertNotNull(blank.validate());
    }

    @Test
    @DisplayName("validate rejects zero or negative amount")
    void validateInvalidAmount() {
        CurrencyAttachment zero = new CurrencyAttachment("tokens", 0);
        assertNotNull(zero.validate());

        CurrencyAttachment negative = new CurrencyAttachment("tokens", -5);
        assertNotNull(negative.validate());
    }

    @Test
    @DisplayName("validate rejects amount exceeding max")
    void validateExceedsMax() {
        CurrencyAttachment.setMaxAmount("tokens", 500);
        CurrencyAttachment over = CurrencyAttachment.tokens(1000);
        String error = over.validate();
        assertNotNull(error);
        assertTrue(error.contains("exceeds maximum"));
    }

    @Test
    @DisplayName("isCurrencyAllowed returns true for known types when no whitelist")
    void isCurrencyAllowedDefaults() {
        assertTrue(CurrencyAttachment.isCurrencyAllowed("tokens"));
        assertTrue(CurrencyAttachment.isCurrencyAllowed("coins"));
        assertTrue(CurrencyAttachment.isCurrencyAllowed("gems"));
        assertTrue(CurrencyAttachment.isCurrencyAllowed("blood_gems"));
        assertTrue(CurrencyAttachment.isCurrencyAllowed("prestige"));
        assertFalse(CurrencyAttachment.isCurrencyAllowed("unknown_currency"));
        assertFalse(CurrencyAttachment.isCurrencyAllowed(null));
        assertFalse(CurrencyAttachment.isCurrencyAllowed(""));
    }

    @Test
    @DisplayName("addAllowedCurrency restricts to whitelist")
    void addAllowedCurrency() {
        CurrencyAttachment.addAllowedCurrency("tokens");
        assertTrue(CurrencyAttachment.isCurrencyAllowed("tokens"));
        assertFalse(CurrencyAttachment.isCurrencyAllowed("coins"));
    }

    @Test
    @DisplayName("removeAllowedCurrency removes from whitelist")
    void removeAllowedCurrency() {
        CurrencyAttachment.addAllowedCurrency("tokens");
        CurrencyAttachment.addAllowedCurrency("coins");
        CurrencyAttachment.removeAllowedCurrency("tokens");

        assertFalse(CurrencyAttachment.isCurrencyAllowed("tokens"));
        assertTrue(CurrencyAttachment.isCurrencyAllowed("coins"));
    }

    @Test
    @DisplayName("canonicalizes aliases (coin -> coins)")
    void canonicalization() {
        CurrencyAttachment.addAllowedCurrency("coins");
        // "coin" is aliased to "coins"
        assertTrue(CurrencyAttachment.isCurrencyAllowed("coin"));
    }

    @Test
    @DisplayName("toJson and fromJson round-trip")
    void jsonRoundTrip() {
        CurrencyAttachment original = CurrencyAttachment.tokens(500);
        JsonObject json = original.toJson();

        CurrencyAttachment restored = CurrencyAttachment.fromJson(json);
        assertNotNull(restored);
        assertEquals("tokens", restored.currencyType());
        assertEquals(500, restored.amount());
    }

    @Test
    @DisplayName("fromJson returns null for invalid JSON")
    void fromJsonInvalid() {
        assertNull(CurrencyAttachment.fromJson(new JsonObject()));
    }

    @Test
    @DisplayName("getMaxAmountFor returns default for unknown")
    void getMaxAmountForDefault() {
        assertEquals(CurrencyAttachment.MAX_AMOUNT, CurrencyAttachment.getMaxAmountFor("tokens"));
    }

    @Test
    @DisplayName("setMaxAmount limits specific currency")
    void setMaxAmount() {
        CurrencyAttachment.setMaxAmount("gems", 100);
        assertEquals(100, CurrencyAttachment.getMaxAmountFor("gems"));
    }
}
