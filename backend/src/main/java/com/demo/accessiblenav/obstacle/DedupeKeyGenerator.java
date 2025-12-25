package com.demo.accessiblenav.obstacle;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates a deterministic dedupe key for obstacle reports based on edge, type and quantized location.
 * Reports with the same dedupe key within a time window are considered duplicates.
 */
@Component
public class DedupeKeyGenerator {

    /**
     * Generate a 32-char hex dedupe key.
     * Location is quantized to ~10m grid to merge nearby reports.
     */
    public String generate(Long edgeId, String type, Double lat, Double lng) {
        long qLat = Math.round((lat != null ? lat : 0.0) * 10000);
        long qLng = Math.round((lng != null ? lng : 0.0) * 10000);
        String raw = edgeId + "|" + type + "|" + qLat + "|" + qLng;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException(e);
        }
    }
}
