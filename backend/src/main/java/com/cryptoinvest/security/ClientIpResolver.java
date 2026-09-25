package com.cryptoinvest.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Only accepts X-Forwarded-For when the immediate peer is explicitly trusted. */
@Component
public class ClientIpResolver {
    private final List<Network> trustedProxies;

    public ClientIpResolver(@Value("${app.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxies = trustedProxyCidrs == null || trustedProxyCidrs.isBlank() ? List.of()
                : java.util.Arrays.stream(trustedProxyCidrs.split(",")).map(String::trim).filter(value -> !value.isEmpty()).map(Network::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        byte[] peerAddress = parseAddress(peer);
        if (peerAddress == null || trustedProxies.stream().noneMatch(network -> network.contains(peerAddress))) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 1024) return peer;
        String[] hops = forwarded.split(",", -1);
        if (hops.length > 16) return peer;
        byte[][] addresses = new byte[hops.length][];
        for (int i = 0; i < hops.length; i++) {
            addresses[i] = parseAddress(hops[i].trim());
            if (addresses[i] == null) return peer;
        }
        for (int i = addresses.length - 1; i >= 0; i--) {
            byte[] candidate = addresses[i];
            if (trustedProxies.stream().noneMatch(network -> network.contains(candidate))) return canonical(candidate);
        }
        return peer;
    }

    private static byte[] parseAddress(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
                String[] octets = value.split("\\.");
                byte[] address = new byte[4];
                for (int i = 0; i < octets.length; i++) {
                    int part = Integer.parseInt(octets[i]);
                    if (part > 255) return null;
                    address[i] = (byte) part;
                }
                return address;
            }
            if (value.indexOf(':') >= 0 && value.matches("[0-9A-Fa-f:.]+")) return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException | NumberFormatException ignored) { }
        return null;
    }

    private static String canonical(byte[] address) {
        try { return InetAddress.getByAddress(address).getHostAddress(); }
        catch (UnknownHostException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Network(byte[] address, int prefix) {
        static Network parse(String cidr) {
            String[] parts = cidr.split("/", -1);
            byte[] address = parts.length > 0 ? parseAddress(parts[0].trim()) : null;
            if (address == null || parts.length > 2) throw new IllegalArgumentException("Invalid trusted proxy CIDR");
            int bits = address.length * 8;
            int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Invalid trusted proxy CIDR");
            return new Network(address, prefix);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != address.length) return false;
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) if (address[i] != candidate[i]) return false;
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (address[fullBytes] & mask) == (candidate[fullBytes] & mask);
        }
    }
}
