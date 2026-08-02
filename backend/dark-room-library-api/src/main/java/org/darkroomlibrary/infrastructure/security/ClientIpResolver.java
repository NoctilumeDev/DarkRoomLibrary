package org.darkroomlibrary.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the request source consistently for ingress limits and login protection.
 */
@Component
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";
    private final boolean trustForwardedHeaders;
    private final List<CidrBlock> trustedProxyCidrs;

    public ClientIpResolver(
            @Value("${security.client-ip.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
            @Value("${security.client-ip.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.trustedProxyCidrs = parseCidrs(trustedProxyCidrs);
        if (trustForwardedHeaders && this.trustedProxyCidrs.isEmpty()) {
            throw new IllegalStateException("启用转发请求头时必须配置受信代理 CIDR 白名单");
        }
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String remoteAddress = normalizedAddress(request.getRemoteAddr());
        if (remoteAddress == null) {
            return UNKNOWN;
        }
        if (!trustForwardedHeaders || !isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedHeader = request.getHeader("X-Forwarded-For");
        if (forwardedHeader != null && !forwardedHeader.isBlank()) {
            List<String> forwardedChain = parseForwardedChain(forwardedHeader);
            if (forwardedChain.isEmpty()) {
                return remoteAddress;
            }
            for (int i = forwardedChain.size() - 1; i >= 0; i--) {
                String address = forwardedChain.get(i);
                if (!isTrustedProxy(address)) {
                    return address;
                }
            }
            return forwardedChain.get(0);
        }

        String realIp = normalizedAddress(request.getHeader("X-Real-IP"));
        return realIp == null ? remoteAddress : realIp;
    }

    public String resolveCurrentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolve(attributes.getRequest());
        }
        return UNKNOWN;
    }

    private List<String> parseForwardedChain(String value) {
        List<String> addresses = new ArrayList<>();
        for (String part : value.split(",")) {
            String address = normalizedAddress(part);
            if (address == null) {
                return List.of();
            }
            addresses.add(address);
        }
        return addresses;
    }

    private boolean isTrustedProxy(String address) {
        byte[] bytes = addressBytes(address);
        if (bytes == null) {
            return false;
        }
        return trustedProxyCidrs.stream().anyMatch(cidr -> cidr.matches(bytes));
    }

    private static List<CidrBlock> parseCidrs(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            return List.of();
        }
        List<CidrBlock> cidrs = new ArrayList<>();
        for (String configuredCidr : configuredCidrs.split(",")) {
            String value = configuredCidr.trim();
            if (value.isEmpty()) {
                continue;
            }
            cidrs.add(CidrBlock.parse(value));
        }
        return List.copyOf(cidrs);
    }

    private static String normalizedAddress(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "unknown".equalsIgnoreCase(normalized)) {
            return null;
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        int zoneIndex = normalized.indexOf('%');
        if (zoneIndex >= 0) {
            normalized = normalized.substring(0, zoneIndex);
        }
        byte[] bytes = addressBytes(normalized);
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static byte[] addressBytes(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.indexOf(':') < 0 && !isIpv4Literal(value)) {
            return null;
        }
        if (value.indexOf(':') >= 0 && !value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int number = Integer.parseInt(part);
            if (number > 255) {
                return false;
            }
        }
        return true;
    }

    private record CidrBlock(byte[] network, int prefixLength) {

        private static CidrBlock parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2) {
                throw new IllegalArgumentException("无效的受信代理 CIDR: " + value);
            }
            String address = normalizedAddress(parts[0]);
            byte[] bytes = addressBytes(address);
            if (bytes == null) {
                throw new IllegalArgumentException("无效的受信代理 CIDR: " + value);
            }
            int maxPrefix = bytes.length * Byte.SIZE;
            int prefix = parts.length == 1 ? maxPrefix : parsePrefix(parts[1], maxPrefix, value);
            byte[] network = bytes.clone();
            clearHostBits(network, prefix);
            return new CidrBlock(network, prefix);
        }

        private boolean matches(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        private static int parsePrefix(String value, int maxPrefix, String cidr) {
            try {
                int prefix = Integer.parseInt(value);
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException("无效的受信代理 CIDR: " + cidr);
                }
                return prefix;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的受信代理 CIDR: " + cidr, e);
            }
        }

        private static void clearHostBits(byte[] address, int prefixLength) {
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits > 0 && fullBytes < address.length) {
                int mask = 0xFF << (Byte.SIZE - remainingBits);
                address[fullBytes] = (byte) (address[fullBytes] & mask);
                fullBytes++;
            }
            for (int i = fullBytes; i < address.length; i++) {
                address[i] = 0;
            }
        }
    }
}
