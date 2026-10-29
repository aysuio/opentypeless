package com.aysuio.opentypeless;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

public final class VoiceImeService extends InputMethodService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private long generation;
    private AudioSession recorder;
    private ProviderClient client;
    private boolean busy;
    private boolean recording;
    private boolean sensitive;
    private String original = "";
    private String output = "";
    private TextView status, preview;
    private Button record, insert, restore;
    @Override public View onCreateInputView() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(12 * getResources().getDisplayMetrics().density); view.setPadding(p,p,p,p);
        view.setBackgroundColor(0xfff4f5f7);
        status = new TextView(this); status.setTextSize(15); view.addView(status);
        ScrollView scroll = new ScrollView(this); preview = new TextView(this); preview.setTextSize(18);
        preview.setPadding(0,p,0,p); scroll.addView(preview); view.addView(scroll, new LinearLayout.LayoutParams(-1, p*8));
        record = button(view, "开始说话", v -> toggleRecording());
        insert = button(view, "插入文字", v -> {
            if (!output.isEmpty() && !busy && !recording && !sensitive && getCurrentInputConnection() != null) {
                if (getCurrentInputConnection().commitText(output, 1)) { cancelAll(); show("已插入"); }
                else show("输入框拒绝插入；请保留预览后重试");
            }
        });
        restore = button(view, "使用识别原文", v -> { output = original; render(); });
        LinearLayout row = new LinearLayout(this); view.addView(row);
        button(row, "取消", v -> { cancelAll(); show("已取消"); });
        button(row, "换键盘", v -> { cancelAll(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); });
        button(row, "设置", v -> { cancelAll(); startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
        render(); return view;
    }
    private Button button(LinearLayout layout, String text, View.OnClickListener callback) {
        Button b = new Button(this); b.setText(text); b.setOnClickListener(callback); layout.addView(b); return b;
    }
    private void show(String text) { if (status != null) status.setText(text); }
    private void render() {
        if (record == null) return;
        record.setText(recording ? "停止并识别（最长 3 分钟）" : "开始说话");
        record.setEnabled(!busy && !sensitive);
        insert.setEnabled(!output.isEmpty() && !busy && !recording && !sensitive);
        restore.setEnabled(!original.isEmpty() && !busy && !recording);
        preview.setText(output);
    }
    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting); cancelAll();
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        sensitive = (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        render(); show(sensitive ? "密码输入框已禁用云端语音" : "音频发送给 Groq；预览确认后插入");
    }
    @Override public void onFinishInputView(boolean finishingInput) { cancelAll(); super.onFinishInputView(finishingInput); }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        render(); show(sensitive ? "密码输入框已禁用云端语音" : "音频发送给 Groq；预览确认后插入");
    }
    @Override public void onFinishInput() { cancelAll(); super.onFinishInput(); }
    @Override public void onDestroy() { cancelAll(); super.onDestroy(); }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    // Voice controls remain useful when an emulator or a phone has a hardware keyboard.
    @Override public boolean onEvaluateInputViewShown() { super.onEvaluateInputViewShown(); return true; }
    @Override public boolean onShowInputRequested(int flags, boolean configChange) { return true; }
    private void cancelAll() {
        generation++;
        if (recorder != null) recorder.cancel(); recorder = null;
        if (client != null) client.cancel(); client = null;
        busy = false; recording = false; original = ""; output = ""; render();
    }
    private void toggleRecording() {
        if (sensitive || busy) return;
        if (recording) {
            recording = false; busy = true; recorder.stop(); show("正在识别…"); render(); return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { show("先打开设置，授予麦克风权限"); return; }
        SecureSettings settings = new SecureSettings(this);
        try { if (settings.secret("groq_key").isEmpty()) { show("请先在设置填写 Groq API Key"); return; } }
        catch (Exception e) { show("无法读取系统凭据，请重新保存 Key"); return; }
        cancelAll(); long session = generation;
        recorder = new AudioSession(); recording = true; show("正在录音；点停止结束"); render();
        recorder.start((pcm, error) -> main.post(() -> {
            if (session != generation) return;
            recorder = null; recording = false;
            if (error != null) { busy = false; show(error); render(); return; }
            busy = true; show("正在发送给 Groq…"); render();
            ProviderClient request = new ProviderClient(); client = request;
            new Thread(() -> {
                try {
                    String raw = request.transcribe(pcm, settings);
                    main.post(() -> { if (session == generation) { original = raw; output = raw; render(); show("原文已识别" ); } });
                    String result = raw; String message = "识别完成，请预览后插入";
                    if (settings.preferences.getBoolean("polish", false)) {
                        try { result = request.polish(raw, settings, this); message = "Google 已整理；可切回原文，请检查数字和术语"; }
                        catch (Exception e) { message = "Google 整理失败，已保留识别原文"; }
                    }
                    String finalResult = result; String finalMessage = message;
                    main.post(() -> { if (session == generation) { busy = false; output = finalResult; client = null; show(finalMessage); render(); } });
                } catch (Exception e) {
                    main.post(() -> { if (session == generation) { busy = false; client = null; show("识别失败。请检查网络、Key 和额度后重试。"); render(); } });
                } finally { java.util.Arrays.fill(pcm, (byte)0); }
            }, "opentypeless-provider").start();
        }));
    }
}
