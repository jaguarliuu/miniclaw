package com.jaguarliu.ai.nodeconsole;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 凭据加密器
 * AES/GCM/NoPadding, 256-bit key, 12-byte random IV
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final NodeConsoleProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        String key = properties.getEncryptionKey();

        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "NODE_CONSOLE_ENCRYPTION_KEY environment variable is required. " +
                    "Generate a key with: openssl rand -hex 32"
            );
        }

        if (key.length() != 64) {
            throw new IllegalStateException(
                    "Encryption key must be 64 hex characters (32 bytes for AES-256). " +
                    "Current length: " + key.length() + ". " +
                    "Generate a valid key with: openssl rand -hex 32"
            );
        }

        try {
            byte[] keyBytes = HexFormat.of().parseHex(key);
            this.keySpec = new SecretKeySpec(keyBytes, "AES");
            log.info("CredentialCipher initialized with AES-256-GCM");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize encryption key: " + e.getMessage() + ". " +
                    "Ensure the key is valid hex format (64 characters). " +
                    "Generate a valid key with: openssl rand -hex 32",
                    e
            );
        }
    }

    /**
     * 加密明文凭据
     *
     * @param plaintext 明文
     * @return EncryptedPayload(ciphertext base64, iv base64)
     */
    public EncryptedPayload encrypt(String plaintext) {
        ensureKeyAvailable();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 解密凭据
     *
     * @param ciphertextBase64 密文 base64
     * @param ivBase64         IV base64
     * @return 明文
     */
    public String decrypt(String ciphertextBase64, String ivBase64) {
        ensureKeyAvailable();
        try {
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private void ensureKeyAvailable() {
        if (keySpec == null) {
            throw new IllegalStateException(
                    "Encryption key not configured. Set NODE_CONSOLE_ENCRYPTION_KEY environment variable.");
        }
    }

    public record EncryptedPayload(String ciphertext, String iv) {}
}
