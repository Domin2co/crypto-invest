package com.cryptoinvest.exchange.credential;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** DB에 저장할 거래소 자격증명만 AES-GCM으로 암복호화한다. 원문을 로그로 남기지 않는다. */
@Component
public class CredentialCipher {

    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String VERSION = "v1";

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(@Value("${app.credential-encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String plaintext, String context) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Credential must not be blank");
        }

        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(contextBytes(context));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + "." + encode(nonce) + "." + encode(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Credential encryption failed", exception);
        }
    }

    public String decrypt(String encryptedValue, String context) {
        String[] parts = encryptedValue == null ? new String[0] : encryptedValue.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Invalid encrypted credential format");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] nonce = decode(parts[1]);
            if (nonce.length != GCM_NONCE_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted credential format");
            }
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(contextBytes(context));
            return new String(cipher.doFinal(decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Credential decryption failed", exception);
        }
    }

    private SecretKeySpec key() {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("CREDENTIAL_ENCRYPTION_KEY is required for credential operations");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            decoded = Base64.getUrlDecoder().decode(encodedKey);
        }
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("CREDENTIAL_ENCRYPTION_KEY must be base64-encoded 256-bit key");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private static byte[] contextBytes(String context) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("Credential context must not be blank");
        }
        return context.getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
