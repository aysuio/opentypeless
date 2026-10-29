package com.aysuio.opentypeless;

import android.test.AndroidTestCase;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Runs inside the emulator, including a real Android Keystore round-trip. */
@SuppressWarnings("deprecation")
public final class PrivacyContractTest extends AndroidTestCase {
    public void testCredentialRoundTripIsEncrypted() throws Exception {
        SecureSettings settings = new SecureSettings(getContext());
        String name = "instrumentation_test_key";
        try {
            settings.setSecret(name, "test-only-secret-中文");
            assertEquals("test-only-secret-中文", settings.secret(name));
            assertFalse(settings.preferences.getString(name, "").contains("test-only-secret"));
        } finally { settings.preferences.edit().remove(name).commit(); }
    }
    public void testWavHeaderContainsPcmLengthAndRate() {
        byte[] wav = ProviderClient.wav(new byte[]{1,2,3,4});
        assertEquals(48, wav.length);
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(16000, buffer.getInt(24));
        assertEquals(4, buffer.getInt(40));
        assertEquals(1, wav[44]);
    }
    public void testDictionaryBoundPreservesWholeWords() {
        String input = "OpenTypeless, Groq, Kubernetes, 普通话, " + "x".repeat(300);
        String result = ProviderClient.dictionaryPrompt(input);
        assertEquals("OpenTypeless, Groq, Kubernetes, 普通话", result);
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= 200);
        assertEquals("safe", ProviderClient.dictionaryPrompt("bad\u0001word,safe"));
    }
    public void testBackupAndImePermission() throws Exception {
        var info = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), PackageManager.GET_SERVICES);
        assertEquals(0, info.applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        boolean found = false;
        for (var service : info.services) {
            if (service.name.endsWith("VoiceImeService")) {
                assertEquals("android.permission.BIND_INPUT_METHOD", service.permission); found = true;
            }
        }
        assertTrue(found);
    }
}
