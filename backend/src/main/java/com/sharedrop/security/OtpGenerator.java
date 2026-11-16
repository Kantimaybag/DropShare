package com.sharedrop.security;

import java.security.SecureRandom;

/**
 * Generates cryptographically secure OTPs using SecureRandom.
 */
public class OtpGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OtpGenerator() {}

    /**
     * Generates a numeric OTP of the specified length.
     * @param length number of digits (default 6)
     * @return OTP string, zero-padded to length
     */
    public static String generate(int length) {
        int bound = (int) Math.pow(10, length);
        int otp = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + length + "d", otp);
    }

    /** Generates a 6-digit OTP. */
    public static String generate() {
        return generate(6);
    }
}
