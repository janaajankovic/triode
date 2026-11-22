package dev.triode.tickets.domain;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SHA-256 (lowercase hex) of a submission's subject and body exactly as received, before stripping. Two submissions
 * with the same idempotency key are the same request only if their fingerprints are equal.
 *
 * <p>Encoding: for subject, then body, a 4-byte big-endian length (in UTF-16 code units) followed by the code units
 * themselves (UTF-16BE). The length prefix keeps the field boundary unambiguous ({@code "ab"+"c"} differs from
 * {@code "a"+"bc"}). UTF-16 code units are used instead of UTF-8 because {@code String.getBytes(UTF_8)} replaces
 * unpaired surrogates (a JSON string may escape a lone surrogate) with {@code ?}, so different texts could
 * share a fingerprint; the code units are lossless for every Java string.
 *
 * @param value 64 lowercase hex characters
 */
public record RequestFingerprint(String value) {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    /** @throws InvalidTicketException if the value is not 64 lowercase hex characters (invalid stored data) */
    public RequestFingerprint {
        if (value == null || !SHA_256_HEX.matcher(value).matches()) {
            throw new InvalidTicketException("requestFingerprint", "must be 64 lowercase hex characters");
        }
    }

    /**
     * Fingerprint of a submission's raw subject and body.
     *
     * @throws NullPointerException if subject or body is null (validate them first)
     */
    public static RequestFingerprint of(String subject, String body) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
        MessageDigest sha256 = sha256();
        update(sha256, subject);
        update(sha256, body);
        return new RequestFingerprint(HexFormat.of().formatHex(sha256.digest()));
    }

    private static void update(MessageDigest digest, String text) {
        ByteBuffer bytes = ByteBuffer.allocate(Integer.BYTES + Character.BYTES * text.length()); // big-endian
        bytes.putInt(text.length());
        for (int i = 0; i < text.length(); i++) {
            bytes.putChar(text.charAt(i));
        }
        digest.update(bytes.array());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform must support SHA-256", e);
        }
    }
}
