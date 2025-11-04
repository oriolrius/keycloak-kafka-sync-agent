package com.miimetiq.keycloak.sync.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SensitiveDataMasker utility.
 */
public class SensitiveDataMaskerTest {

    @Test
    public void testMaskPassword() {
        String masked = SensitiveDataMasker.maskPassword("mysecretpassword");
        assertEquals("***", masked);
    }

    @Test
    public void testMaskPasswordNull() {
        String masked = SensitiveDataMasker.maskPassword(null);
        assertNull(masked);
    }

    @Test
    public void testMaskIfSensitiveTrue() {
        String masked = SensitiveDataMasker.maskIfSensitive("mysecretvalue", true);
        assertEquals("my***ue", masked);
    }

    @Test
    public void testMaskIfSensitiveFalse() {
        String masked = SensitiveDataMasker.maskIfSensitive("normalvalue", false);
        assertEquals("normalvalue", masked);
    }

    @Test
    public void testMaskIfSensitiveShortValue() {
        String masked = SensitiveDataMasker.maskIfSensitive("abc", true);
        assertEquals("***", masked);
    }

    @Test
    public void testMaskString() {
        String input = "password=secret123";
        String masked = SensitiveDataMasker.mask(input);
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("secret123"));
    }

    @Test
    public void testMaskJaasConfig() {
        String input = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"admin\" password=\"admin-secret\";";
        String masked = SensitiveDataMasker.mask(input);
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("admin-secret"));
    }

    @Test
    public void testMaskBasicAuth() {
        String input = "username:password@host";
        String masked = SensitiveDataMasker.mask(input);
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("password"));
    }

    @Test
    public void testMaskNull() {
        String masked = SensitiveDataMasker.mask(null);
        assertNull(masked);
    }

    @Test
    public void testMaskEmptyString() {
        String masked = SensitiveDataMasker.mask("");
        assertEquals("", masked);
    }
}
