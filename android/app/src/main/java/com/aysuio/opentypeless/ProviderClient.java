package com.aysuio.opentypeless;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Fixed destinations, platform TLS verification, no redirects, no content logging. */
final class ProviderClient {
    private static final String GROQ = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String GOOGLE = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final int MAX_RESPONSE = 1024 * 1024;
    private volatile HttpsURLConnection active;
    private volatile boolean cancelled;
    void cancel() { cancelled = true; HttpsURLConnection c = active; if (c != null) c.disconnect(); }
    private String post(String endpoint, String key, String type, byte[] body) throws Exception {
        if (cancelled) throw new Exception("已取消");
        if (key.trim().isEmpty()) throw new Exception("请先在设置中填写 API Key");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(endpoint).openConnection();
        active = connection;
        try {
            if (cancelled) throw new Exception("已取消");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(60000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());
            connection.setRequestProperty("Content-Type", type);
            connection.setFixedLengthStreamingMode(body.length);
            try (var out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new Exception("服务返回 HTTP " + status + "，请检查 Key、余额及模型权限");
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled) throw new Exception("已取消");
                    if (result.size() + count > MAX_RESPONSE) throw new Exception("服务响应过大");
                    result.write(buffer, 0, count);
                }
                return result.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); active = null; }
    }
    static String dictionaryPrompt(String words) {
        StringBuilder result = new StringBuilder();
        for (String word : words.split("[,，\\n]")) {
            word = word.trim();
            if (word.isEmpty() || word.codePoints().anyMatch(Character::isISOControl)) continue;
            String next = (result.length() == 0 ? "" : ", ") + word;
            if ((result.toString() + next).getBytes(StandardCharsets.UTF_8).length <= 200) result.append(next);
        }
        return result.toString();
    }
    static byte[] wav(byte[] pcm) {
        var buffer = java.nio.ByteBuffer.allocate(44 + pcm.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
        buffer.putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return buffer.array();
    }
    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    String transcribe(byte[] pcm, SecureSettings settings) throws Exception {
        String boundary = "ot-" + UUID.randomUUID();
        var out = new ByteArrayOutputStream();
        field(out, boundary, "model", settings.model());
        field(out, boundary, "temperature", "0");
        field(out, boundary, "response_format", "json");
        // Omit language for Mandarin-English code switching; never translate.
        String prompt = dictionaryPrompt(settings.preferences.getString("hotwords", ""));
        if (!prompt.isEmpty()) field(out, boundary, "prompt", prompt);
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(wav(pcm)); out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        JSONObject result = new JSONObject(post(GROQ, settings.secret("groq_key"), "multipart/form-data; boundary=" + boundary, out.toByteArray()));
        String text = result.getString("text").trim();
        if (text.isEmpty()) throw new Exception("没有识别到语音，请重新录音");
        return text;
    }
    String polish(String raw, SecureSettings settings, Context context) throws Exception {
        String instructions;
        try (InputStream input = context.getAssets().open("polish-instructions.txt")) {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) content.write(buffer, 0, count);
            instructions = content.toString(StandardCharsets.UTF_8.name());
        }
        JSONArray messages = new JSONArray()
            .put(new JSONObject().put("role", "system").put("content", instructions))
            .put(new JSONObject().put("role", "user").put("content", raw));
        String model = settings.preferences.getString("google_model", "gemini-3.8-flash");
        JSONObject request = new JSONObject().put("model", model).put("messages", messages).put("stream", false);
        JSONObject result = new JSONObject(post(GOOGLE, settings.secret("google_key"), "application/json", request.toString().getBytes(StandardCharsets.UTF_8)));
        String text = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        if (text.isEmpty()) throw new Exception("Google 返回空结果，已保留原文");
        return text;
    }
}
