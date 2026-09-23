package com.cryptoinvest.exchange.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {

    private static final String KEY = standardBase64Key();
    private static String standardBase64Key() { byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 0xfb); return Base64.getEncoder().encodeToString(key); }
    private final CredentialCipher cipher = new CredentialCipher(KEY);

    @Test
    void encryptsWithRandomNonceAndDecryptsOnlyForTheSameContext() {
        String first = cipher.encrypt("access-key", "account-id:access");
        String second = cipher.encrypt("access-key", "account-id:access");

        assertThat(first).isNotEqualTo(second).doesNotContain("access-key");
        assertThat(cipher.decrypt(first, "account-id:access")).isEqualTo("access-key");
        assertThatThrownBy(() -> cipher.decrypt(first, "other-account:access"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsUrlSafeKeyForExistingConfigurations() {
        byte[] decoded = Base64.getDecoder().decode(KEY);
        String urlKey = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        CredentialCipher urlCipher = new CredentialCipher(urlKey);
        assertThat(urlCipher.decrypt(urlCipher.encrypt("secret", "account"), "account")).isEqualTo("secret");
    }

    @Test
    void rejectsMissingMasterKey() {
        assertThatThrownBy(() -> new CredentialCipher("").encrypt("access-key", "account-id:access"))
                .isInstanceOf(IllegalStateException.class);
    }
}
