package com.cryptoinvest.exchange.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
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
    void rejectsMissingMasterKey() {
        assertThatThrownBy(() -> new CredentialCipher("").encrypt("access-key", "account-id:access"))
                .isInstanceOf(IllegalStateException.class);
    }
}
