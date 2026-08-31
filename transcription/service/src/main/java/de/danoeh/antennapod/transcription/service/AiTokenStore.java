package de.danoeh.antennapod.transcription.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class AiTokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "AntennaPod.Transcription.AiKey";
    private static final String PREFS = "local_transcription_ai_key";
    private static final String IV = "iv";
    private static final String CIPHERTEXT = "ciphertext";

    private final SharedPreferences preferences;

    AiTokenStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(String value) {
        if (value.isBlank()) {
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            preferences.edit()
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not store AI API key", e);
        }
    }

    String load() {
        String iv = preferences.getString(IV, null);
        String encrypted = preferences.getString(CIPHERTEXT, null);
        if (iv == null || encrypted == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read AI API key", e);
        }
    }

    boolean hasValue() {
        return preferences.contains(IV) && preferences.contains(CIPHERTEXT);
    }

    private SecretKey key() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        try {
            keyStore.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
