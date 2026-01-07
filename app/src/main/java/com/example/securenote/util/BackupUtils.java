package com.example.securenote.util;

import android.util.Base64;

import com.example.securenote.model.Note;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class BackupUtils {

    // ---------- PBKDF2 + AES-GCM parameters for BACKUP (separate from CryptoManager) ----------

    private static final int SALT_LENGTH_BYTES = 16;          // 128-bit salt
    private static final int IV_LENGTH_BYTES = 12;            // GCM recommended IV size
    private static final int KEY_LENGTH_BITS = 256;           // AES-256
    private static final int PBKDF2_ITERATIONS = 20_000;      // For backup; enough but not crazy
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String PBKDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom secureRandom = new SecureRandom();

    // ---------- STEP 1: list<Note> → JSON (NOT decrypted) ----------

    public static String notesToJson(List<Note> notes) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("exported_at", System.currentTimeMillis());

            JSONArray array = new JSONArray();

            if (notes != null) {
                for (Note n : notes) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", n.getId());
                    obj.put("timestamp", n.getTimestamp());
                    obj.put("pinned", n.isPinned());
                    obj.put("locked", n.isLocked());
                    obj.put("lockPassword", n.getLockPassword());
                    obj.put("encryptedTitle", n.getEncryptedTitle());
                    obj.put("encryptedContent", n.getEncryptedContent());
                    obj.put("inTrash", n.isInTrash());
                    array.put(obj);
                }
            }

            root.put("notes", array);
            return root.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build JSON backup", e);
        }
    }

    // ---------- STEP 2: Encrypt JSON with backup password ----------

    /**
     * Encrypts a JSON string using a user-provided backup password.
     * Returns Base64( salt || iv || ciphertext ).
     */
    public static String encryptBackup(String plainJson, String backupPassword) {
        try {
            byte[] salt = generateRandomBytes(SALT_LENGTH_BYTES);
            byte[] iv = generateRandomBytes(IV_LENGTH_BYTES);

            SecretKey key = deriveKeyFromPassword(backupPassword, salt);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] plainBytes = plainJson.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            // Concatenate salt || iv || ciphertext
            byte[] out = new byte[salt.length + iv.length + cipherBytes.length];
            System.arraycopy(salt, 0, out, 0, salt.length);
            System.arraycopy(iv, 0, out, salt.length, iv.length);
            System.arraycopy(cipherBytes, 0, out, salt.length + iv.length, cipherBytes.length);

            return Base64.encodeToString(out, Base64.NO_WRAP);

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt backup", e);
        }
    }

    // ---------- STEP 3: Decrypt backup with backup password ----------

    /**
     * Decrypts Base64( salt || iv || ciphertext ) using backup password.
     * Returns the original JSON string created by notesToJson().
     */
    public static String decryptBackup(String encryptedBase64, String backupPassword) {
        try {
            byte[] all = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            if (all.length < SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 1) {
                throw new IllegalArgumentException("Invalid backup data");
            }

            byte[] salt = new byte[SALT_LENGTH_BYTES];
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[all.length - SALT_LENGTH_BYTES - IV_LENGTH_BYTES];

            System.arraycopy(all, 0, salt, 0, SALT_LENGTH_BYTES);
            System.arraycopy(all, SALT_LENGTH_BYTES, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(all, SALT_LENGTH_BYTES + IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            SecretKey key = deriveKeyFromPassword(backupPassword, salt);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt backup", e);
        }
    }

    // ---------- STEP 4: JSON → list<Note> (will use this for import) ----------

    public static List<Note> jsonToNotes(String jsonString) {
        try {
            List<Note> notes = new ArrayList<>();
            JSONObject root = new JSONObject(jsonString);

            JSONArray array = root.optJSONArray("notes");
            if (array == null) return notes;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);

                Note n = new Note();
                n.setId(obj.getString("id"));
                n.setTimestamp(obj.getLong("timestamp"));
                n.setPinned(obj.getBoolean("pinned"));
                n.setLocked(obj.getBoolean("locked"));
                // this is the NOTE lock password hash (per-note), not master PIN:
                if (!obj.isNull("lockPassword")) {
                    n.setLockPassword(obj.getString("lockPassword"));
                }
                n.setEncryptedTitle(obj.optString("encryptedTitle", null));
                n.setEncryptedContent(obj.optString("encryptedContent", null));
                n.setInTrash(obj.getBoolean("inTrash"));

                notes.add(n);
            }

            return notes;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON backup", e);
        }
    }

    // ---------- Internal helpers ----------

    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static SecretKey deriveKeyFromPassword(String password, byte[] salt) throws Exception {
        char[] chars = password.toCharArray();
        KeySpec spec = new PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF_ALGO);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
