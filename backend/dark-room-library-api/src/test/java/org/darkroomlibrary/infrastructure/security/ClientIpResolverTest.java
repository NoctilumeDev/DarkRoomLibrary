package org.darkroomlibrary.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenDirectPeerIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8");
        MockHttpServletRequest request = request("198.51.100.10", "192.0.2.30");

        assertEquals("198.51.100.10", resolver.resolve(request));
    }

    @Test
    void resolvesFirstUntrustedAddressFromRightOfForwardedChain() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8,192.168.0.0/16");
        MockHttpServletRequest request = request(
                "10.0.0.5",
                "203.0.113.250, 198.51.100.20, 192.168.1.8");

        assertEquals("198.51.100.20", resolver.resolve(request));
    }

    @Test
    void acceptsRealIpOnlyFromTrustedDirectPeer() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        MockHttpServletRequest request = request("127.0.0.1", null);
        request.addHeader("X-Real-IP", "203.0.113.8");

        assertEquals("203.0.113.8", resolver.resolve(request));
    }

    @Test
    void supportsIpv6ProxyCidrsAndCanonicalizesEquivalentForms() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver(true, "2001:db8:1::/48");
        MockHttpServletRequest request = request("2001:db8:1::20", "2001:db8:2::30");

        assertEquals(
                InetAddress.getByName("2001:db8:2:0:0:0:0:30").getHostAddress(),
                resolver.resolve(request));
    }

    @Test
    void rejectsMalformedForwardedChainInsteadOfPartiallyTrustingIt() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8");
        MockHttpServletRequest request = request("10.0.0.5", "203.0.113.8,not-an-ip");

        assertEquals("10.0.0.5", resolver.resolve(request));
    }

    @Test
    void requiresAllowlistWhenForwardedHeadersAreEnabled() {
        assertThrows(IllegalStateException.class, () -> new ClientIpResolver(true, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientIpResolver(true, "10.0.0.0/99"));
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedAddress != null) {
            request.addHeader("X-Forwarded-For", forwardedAddress);
        }
        return request;
    }
}
