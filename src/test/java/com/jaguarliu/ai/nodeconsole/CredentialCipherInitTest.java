package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialCipherInitTest {

    @Test
    void testMissingKeyThrowsAtStartup() {
        NodeConsoleProperties props = new NodeConsoleProperties();
        props.setEncryptionKey(null);

        CredentialCipher cipher = new CredentialCipher(props);

        IllegalStateException ex = assertThrows(IllegalStateException.class, cipher::init);
        assertTrue(ex.getMessage().contains("required"));
        assertTrue(ex.getMessage().contains("openssl rand -hex 32"));
    }

    @Test
    void testInvalidKeyLengthThrowsAtStartup() {
        NodeConsoleProperties props = new NodeConsoleProperties();
        props.setEncryptionKey("tooshort");

        CredentialCipher cipher = new CredentialCipher(props);

        IllegalStateException ex = assertThrows(IllegalStateException.class, cipher::init);
        assertTrue(ex.getMessage().contains("64 hex characters"));
        assertTrue(ex.getMessage().contains("openssl rand -hex 32"));
    }

    @Test
    void testInvalidHexFormatThrowsAtStartup() {
        NodeConsoleProperties props = new NodeConsoleProperties();
        // 64 characters but not valid hex
        props.setEncryptionKey("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz");

        CredentialCipher cipher = new CredentialCipher(props);

        IllegalStateException ex = assertThrows(IllegalStateException.class, cipher::init);
        assertTrue(ex.getMessage().contains("Failed to initialize"));
        assertTrue(ex.getMessage().contains("openssl rand -hex 32"));
    }

    @Test
    void testValidKeySucceeds() {
        NodeConsoleProperties props = new NodeConsoleProperties();
        // Valid 64-character hex string (32 bytes)
        props.setEncryptionKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        CredentialCipher cipher = new CredentialCipher(props);

        assertDoesNotThrow(cipher::init);

        // Test encrypt/decrypt works
        String plaintext = "test-password-123";
        var encrypted = cipher.encrypt(plaintext);
        String decrypted = cipher.decrypt(encrypted.ciphertext(), encrypted.iv());

        assertEquals(plaintext, decrypted);
    }
}
