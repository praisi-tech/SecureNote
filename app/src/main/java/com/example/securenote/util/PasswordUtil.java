package com.example.securenote.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import android.util.Base64;

public class PasswordUtil {

    private static final int SALT_LENGTH = 16;

    /**
     * Hashes the password using SHA-256 with a strong, random salt.
     * The returned string contains both the salt and the hash, separated by a colon.
     */
    public static String hashLockPassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }

        try {
            // 1. Generate a Salt
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // 2. Hash the password + salt
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());

            // 3. Combine salt and hash into a single string for storage
            String saltStr = Base64.encodeToString(salt, Base64.NO_WRAP);
            String hashStr = Base64.encodeToString(hashedPassword, Base64.NO_WRAP);

            return saltStr + ":" + hashStr;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing failed: SHA-256 not available.", e);
        }
    }

    /**
     * Verifies the given password against the stored salt:hash string.
     */
    public static boolean verifyLockPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        try {
            // 1. Extract salt and stored hash
            String[] parts = storedHash.split(":");
            if (parts.length != 2) return false;
            byte[] salt = Base64.decode(parts[0], Base64.DEFAULT);
            String storedHashValue = parts[1];

            // 2. Hash the raw password using the retrieved salt
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedAttempt = md.digest(rawPassword.getBytes());
            String hashedAttemptStr = Base64.encodeToString(hashedAttempt, Base64.NO_WRAP);

            // 3. Compare the generated hash with the stored hash
            return hashedAttemptStr.equals(storedHashValue);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Verification failed: SHA-256 not available.", e);
        } catch (IllegalArgumentException e) {
            // Handles Base64 decode errors
            return false;
        }
    }
}