package com.erp.manufacturing.common.security;

import com.erp.manufacturing.config.SecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the real client IP address using the Trusted Proxy Design.
 * <p>
 * <b>Security principle:</b> Only trust {@code X-Forwarded-For} when the request arrives
 * from a known trusted proxy (nginx/LB we control). If not, use {@code RemoteAddr} directly
 * to prevent IP spoofing via crafted headers.
 * <p>
 * <b>XFF traversal:</b> Traverse right-to-left, skipping trusted hops.
 * The first non-trusted IP is the real client address.
 */
@Component
@RequiredArgsConstructor
public class IpExtractor {

    private final SecurityProperties securityProperties;
    private List<CidrRange> trustedRanges;

    @PostConstruct
    void init() {
        trustedRanges = new ArrayList<>();
        for (String cidr : securityProperties.trustedProxies()) {
            try {
                trustedRanges.add(CidrRange.parse(cidr));
            } catch (Exception e) {
                throw new IllegalStateException("Invalid trusted proxy CIDR: " + cidr, e);
            }
        }
    }

    /**
     * Returns the real client IP.
     * <ul>
     *   <li>If {@code remoteAddr} is NOT a trusted proxy → return it directly (ignore XFF).</li>
     *   <li>If {@code remoteAddr} IS trusted → parse XFF right-to-left, return first non-trusted IP.</li>
     * </ul>
     */
    public String extract(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (!isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }
        if (xff.length() > 2048) {
            return remoteAddr;
        }

        // XFF: "client, proxy1, proxy2" – traverse right-to-left
        String[] hops = xff.split(",");
        if (hops.length > 20) {
            return remoteAddr;
        }
        for (int i = hops.length - 1; i >= 0; i--) {
            String ip = hops[i].trim();
            if (isValidIp(ip) && !isTrusted(ip)) {
                return ip;
            }
        }

        // All hops trusted (rare edge case) – use leftmost
        return hops[0].trim();
    }

    private boolean isTrusted(String ip) {
        return trustedRanges.stream().anyMatch(r -> r.contains(ip));
    }

    private boolean isValidIp(String ip) {
        return isValidAddressLiteral(ip);
    }

    private static boolean isValidAddressLiteral(String ip) {
        if (ip == null || ip.isBlank()) return false;
        if (ip.contains(":")) return ip.matches("^[0-9a-fA-F:.]+$");
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (!octet.matches("\\d{1,3}") || Integer.parseInt(octet) > 255) return false;
        }
        return true;
    }

    // ── Simple CIDR range implementation ───────────────────────────────────

    private record CidrRange(byte[] networkAddress, int prefixLength) {

        static CidrRange parse(String cidr) {
            if (!cidr.contains("/")) {
                // Exact IP
                try {
                    if (!isValidAddressLiteral(cidr)) throw new IllegalArgumentException("Invalid IP: " + cidr);
                    byte[] address = InetAddress.getByName(cidr).getAddress();
                    return new CidrRange(address, address.length * 8);
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("Invalid IP: " + cidr);
                }
            }
            String[] parts = cidr.split("/");
            try {
                if (parts.length != 2 || !isValidAddressLiteral(parts[0])) {
                    throw new IllegalArgumentException("Invalid CIDR: " + cidr);
                }
                byte[] addr   = InetAddress.getByName(parts[0]).getAddress();
                int    prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > addr.length * 8) {
                    throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr);
                }
                return new CidrRange(addr, prefix);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }
        }

        boolean contains(String ip) {
            try {
                if (!isValidAddressLiteral(ip)) return false;
                byte[] target = InetAddress.getByName(ip).getAddress();
                if (target.length != networkAddress.length) return false;
                int bits = prefixLength;
                for (int i = 0; i < target.length && bits > 0; i++) {
                    int mask = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
                    if ((target[i] & mask) != (networkAddress[i] & mask)) return false;
                    bits -= 8;
                }
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
