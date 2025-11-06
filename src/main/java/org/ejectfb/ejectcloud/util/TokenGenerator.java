package org.ejectfb.ejectcloud.util;

import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {
    private static final SecureRandom random = new SecureRandom();

    public static String generate(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        // URL-safe base64 without padding
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String generateLong() {
        return generate(48); // ~64+ chars base64 url-safe
    }
}
