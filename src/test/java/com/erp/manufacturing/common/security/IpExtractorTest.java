package com.erp.manufacturing.common.security;

import com.erp.manufacturing.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpExtractor Unit Tests – Trusted Proxy Logic")
class IpExtractorTest {

    private IpExtractor ipExtractor;

    @BeforeEach
    void setUp() throws Exception {
        SecurityProperties props = new SecurityProperties(
                List.of("127.0.0.1", "10.0.0.0/8", "192.168.0.0/16")
        );
        ipExtractor = new IpExtractor(props);
        // Call @PostConstruct manually
        var init = IpExtractor.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(ipExtractor);
    }

    @Test
    @DisplayName("Direct request (non-trusted remote addr) – uses remoteAddr, ignores XFF")
    void directRequest_usesRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.5");
        req.addHeader("X-Forwarded-For", "1.2.3.4"); // attacker-crafted

        assertThat(ipExtractor.extract(req)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Request via trusted proxy – uses XFF client IP")
    void requestViaTrustedProxy_usesXffClientIp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1"); // trusted nginx
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(ipExtractor.extract(req)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("XFF with multiple hops – returns first non-trusted from right")
    void xffMultipleHops_returnsFirstNonTrusted() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1"); // trusted
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2, 10.0.0.1");
        // Traverse right: 10.0.0.1 (trusted), 10.0.0.2 (trusted), 203.0.113.5 ← client

        assertThat(ipExtractor.extract(req)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Spoofed XFF on direct request – spoofed IP ignored")
    void spoofedXff_onDirectRequest_isIgnored() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.99"); // not trusted
        req.addHeader("X-Forwarded-For", "1.1.1.1"); // spoofed

        assertThat(ipExtractor.extract(req)).isEqualTo("203.0.113.99");
    }

    @Test
    @DisplayName("VPN user – returns VPN server IP as client IP")
    void vpnUser_returnsVpnIpAsClientIp() {
        // User → VPN(5.6.7.8) → nginx(10.0.0.1) → App
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "5.6.7.8");

        assertThat(ipExtractor.extract(req)).isEqualTo("5.6.7.8");
    }

    @Test
    @DisplayName("No XFF header – returns remote addr")
    void noXffHeader_returnsRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1"); // trusted

        assertThat(ipExtractor.extract(req)).isEqualTo("10.0.0.1");
    }
}
