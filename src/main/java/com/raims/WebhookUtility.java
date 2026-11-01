package com.raims;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.raims.exception.SignatureVerificationException;
import com.raims.internal.JsonSupport;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticates the original body bytes of a Raims webhook delivery. */
public final class WebhookUtility {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(62);
    private WebhookUtility() {}

    public static void verifySignature(byte[] payload, Map<String, ?> headers, String secret) {
        verifySignature(payload, headers, secret, 300, Instant.now().getEpochSecond());
    }

    public static void verifySignature(String payload, Map<String, ?> headers, String secret) {
        verifySignature(encode(payload), headers, secret);
    }

    public static void verifySignature(byte[] payload, Map<String, ?> headers, String secret,
            long toleranceSeconds, long nowSeconds) {
        if (toleranceSeconds < 0 || nowSeconds < 0) throw new IllegalArgumentException("Webhook tolerance and clock must be non-negative.");
        if (payload == null) throw failure("The webhook body must contain the original HTTP bytes.");
        String id = header(headers, "webhook-id");
        String timestamp = header(headers, "webhook-timestamp");
        String signature = header(headers, "webhook-signature");
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw failure("The webhook delivery ID is invalid.");
        }
        if (!timestamp.matches("[0-9]{1,12}")) throw failure("The webhook timestamp is invalid or outside the allowed tolerance.");
        long time = Long.parseLong(timestamp);
        long difference = time > nowSeconds ? time - nowSeconds : nowSeconds - time;
        if (difference > toleranceSeconds) throw failure("The webhook timestamp is invalid or outside the allowed tolerance.");
        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(decodeSecret(secret), "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            expected = mac.doFinal(payload);
        } catch (GeneralSecurityException error) {
            throw failure("Webhook authentication could not be initialized.");
        }
        boolean matched = false;
        for (String candidate : signature.split(",")) {
            candidate = candidate.trim();
            if (!candidate.matches("(?i)v1=[0-9a-f]{64}")) continue;
            byte[] actual = new byte[32];
            for (int index = 0; index < actual.length; index++) {
                actual[index] = (byte) Integer.parseInt(candidate.substring(3 + index * 2, 5 + index * 2), 16);
            }
            matched |= MessageDigest.isEqual(expected, actual);
        }
        if (!matched) throw failure("No webhook signature matches the configured secret.");
    }

    public static void verifySignature(String payload, Map<String, ?> headers, String secret,
            long toleranceSeconds, long nowSeconds) {
        verifySignature(encode(payload), headers, secret, toleranceSeconds, nowSeconds);
    }

    public static JsonObject constructEvent(byte[] payload, Map<String, ?> headers, String secret) {
        return constructEvent(payload, headers, secret, 300, Instant.now().getEpochSecond());
    }

    public static JsonObject constructEvent(String payload, Map<String, ?> headers, String secret) {
        return constructEvent(encode(payload), headers, secret);
    }

    public static JsonObject constructEvent(byte[] payload, Map<String, ?> headers, String secret,
            long toleranceSeconds, long nowSeconds) {
        verifySignature(payload, headers, secret, toleranceSeconds, nowSeconds);
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString();
            JsonElement event = JsonSupport.parse(text);
            if (!event.isJsonObject()) throw failure("The authenticated webhook body must be a JSON object.");
            return event.getAsJsonObject();
        } catch (CharacterCodingException | IllegalArgumentException error) {
            throw failure("The authenticated webhook body is not valid UTF-8 JSON.");
        }
    }

    public static JsonObject constructEvent(String payload, Map<String, ?> headers, String secret,
            long toleranceSeconds, long nowSeconds) {
        return constructEvent(encode(payload), headers, secret, toleranceSeconds, nowSeconds);
    }

    private static String header(Map<String, ?> headers, String name) {
        if (headers == null) throw failure("Webhook headers must be an HTTP header collection.");
        Object result = null; int count = 0;
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) { result = entry.getValue(); count++; }
        }
        if (count != 1) throw failure("A webhook signing header is missing or occurs more than once.");
        if (result instanceof Collection) {
            Collection<?> values = (Collection<?>) result;
            if (values.size() != 1) throw failure("A webhook signing header is missing or occurs more than once.");
            result = values.iterator().next();
        }
        if (!(result instanceof String) || ((String) result).isEmpty()
                || ((String) result).matches("(?s).*[\\x00-\\x08\\x0a-\\x1f\\x7f].*")) {
            throw failure("A webhook signing header is invalid.");
        }
        return (String) result;
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || !secret.matches("whsec_[0-9A-Za-z]{1,43}")) throw failure("The webhook signing secret is invalid.");
        String encoded = secret.substring(6);
        if (encoded.length() > 1 && encoded.charAt(0) == '0') throw failure("The webhook signing secret is invalid.");
        BigInteger value = BigInteger.ZERO;
        for (int index = 0; index < encoded.length(); index++) {
            value = value.multiply(BASE).add(BigInteger.valueOf(ALPHABET.indexOf(encoded.charAt(index))));
        }
        if (value.bitLength() > 256) throw failure("The webhook signing secret is invalid.");
        byte[] decoded = new byte[32];
        for (int index = 0; index < decoded.length; index++) {
            decoded[index] = value.byteValue(); value = value.shiftRight(8);
        }
        return decoded;
    }

    private static byte[] encode(String payload) {
        if (payload == null) throw failure("The webhook body must contain the original HTTP bytes.");
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(payload));
            byte[] result = new byte[encoded.remaining()]; encoded.get(result); return result;
        } catch (CharacterCodingException error) { throw failure("The webhook body is not valid UTF-8 text."); }
    }

    private static SignatureVerificationException failure(String message) { return new SignatureVerificationException(message); }
}
