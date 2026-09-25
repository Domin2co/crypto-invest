package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test
    void ignoresForwardedHeaderUnlessPeerIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.2");
        request.addHeader("X-Forwarded-For", "203.0.113.44");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.2");
    }

    @Test
    void walksTrustedProxyChainFromNearestHopAndRejectsMalformedHeader() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8,2001:db8::/32");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.77, 203.0.113.9, 10.0.0.1");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "attacker.invalid");
        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.2");
    }
}
