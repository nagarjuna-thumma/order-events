package dev.nagarjuna.fulfilment.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signature sent as {@code X-Webhook-Signature: t=<unix seconds>,v1=<hex>}.
 * The timestamp is part of what's signed, so a captured request can't be replayed later.
 * {@link #verify} is what a receiver would run.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-Webhook-Signature";

    private WebhookSignature() {
    }

    public static String sign(String secret, Instant timestamp, String body) {
        long t = timestamp.getEpochSecond();
        return "t=" + t + ",v1=" + hmac(secret, t + "." + body);
    }

    public static boolean verify(String secret, String header, String body, Instant now, Duration tolerance) {
        if (header == null) return false;
        Long t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try {
                    t = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (t == null || v1 == null) return false;
        if (Duration.between(Instant.ofEpochSecond(t), now).abs().compareTo(tolerance) > 0) return false;
        // Constant-time compare, so the check doesn't leak how many characters matched.
        return MessageDigest.isEqual(hmac(secret, t + "." + body).getBytes(StandardCharsets.US_ASCII),
                v1.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
