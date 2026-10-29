package com.aysuio.opentypeless;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** API keys are encrypted with a non-exportable Android Keystore key. */
final class SecureSettings {
    private static final String ALIAS = "opentypeless.personal.credentials.v1";
    final SharedPreferences preferences;
    SecureSettings(Context context) { preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    private synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    synchronized void setSecret(String name, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!preferences.edit().putString(name, encrypted).commit()) throw new Exception("Credential save failed");
    }
    synchronized String secret(String name) throws Exception {
        String value = preferences.getString(name, "");
        if (value.isEmpty()) return "";
        String[] parts = value.split(":", 2);
        if (parts.length != 2) throw new Exception("Invalid stored credential");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
    }
    void clearKeys() { preferences.edit().remove("groq_key").remove("google_key").apply(); }
    String model() { return preferences.getBoolean("turbo", false) ? "whisper-large-v3-turbo" : "whisper-large-v3"; }
}
