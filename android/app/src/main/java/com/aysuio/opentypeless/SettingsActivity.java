package com.aysuio.opentypeless;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public final class SettingsActivity extends Activity {
    private LinearLayout layout;
    private SecureSettings settings;
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        settings = new SecureSettings(this);
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad); scroll.addView(layout); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            layout.setPadding(pad, pad + insets.getSystemWindowInsetTop(), pad, pad + insets.getSystemWindowInsetBottom()); return insets;
        });
        text("OpenTypeless Personal", 24);
        text("语音键盘 · 普通话 / English", 17);
        text("录音直传 Groq。可选 Google 轻度整理，只发送识别文字。没有上游账号、遥测或录音历史；API Key 加密保存在本机。", 15);
        EditText groq = input("Groq API Key（留空保留现有值）", "", true);
        EditText google = input("Google Gemini API Key（可选）", "", true);
        EditText model = input("Google 模型", settings.preferences.getString("google_model", "gemini-3.8-flash"), false);
        Switch polish = new Switch(this); polish.setText("使用 Google 整理文字（可能改变原意，请预览）");
        polish.setChecked(settings.preferences.getBoolean("polish", false)); layout.addView(polish);
        Switch turbo = new Switch(this); turbo.setText("使用 Turbo（更快）；默认使用完整 Large v3");
        turbo.setChecked(settings.preferences.getBoolean("turbo", false)); layout.addView(turbo);
        EditText hotwords = input("术语词典（逗号分隔，只发送前 200 字节内可容纳的词）", settings.preferences.getString("hotwords", ""), false);
        hotwords.setSingleLine(false); hotwords.setMinLines(2); hotwords.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});
        status = text("", 14);
        button("保存设置", v -> {
            try {
                String chosen = model.getText().toString().trim();
                if (!chosen.matches("[a-zA-Z0-9._-]{1,100}")) throw new Exception("模型名称格式不正确");
                if (!groq.getText().toString().trim().isEmpty()) settings.setSecret("groq_key", groq.getText().toString().trim());
                if (!google.getText().toString().trim().isEmpty()) settings.setSecret("google_key", google.getText().toString().trim());
                if (polish.isChecked() && settings.secret("google_key").isEmpty()) throw new Exception("请先填写 Google API Key，或关闭整理");
                boolean saved = settings.preferences.edit().putString("google_model", chosen).putString("hotwords", hotwords.getText().toString())
                    .putBoolean("polish", polish.isChecked()).putBoolean("turbo", turbo.isChecked()).commit();
                if (!saved) throw new Exception("设置保存失败");
                groq.setText(""); google.setText("");
                status.setText(settings.secret("groq_key").isEmpty() ? "设置已保存；尚未填写 Groq Key，暂时不能识别。" : "已保存。可在下方启用并测试键盘。");
            } catch (Exception e) { status.setText("保存失败，请检查设置和系统凭据存储。"); }
        });
        button("1. 授予麦克风权限", v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1));
        button("2. 启用语音键盘", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("3. 切换键盘", v -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        text("使用方式：在任意输入框切换到 OpenTypeless，点开始说话，再点停止。预览后点“插入文字”。切换输入框或隐藏键盘会取消录音和待插入内容。", 15);
        EditText demo = input("在这里测试输入（不保存）", "", false);
        demo.setSingleLine(false); demo.setMinLines(3);
        button("删除本机 API Key", v -> new android.app.AlertDialog.Builder(this).setMessage("删除保存的 Groq 和 Google Key？")
            .setNegativeButton("取消", null).setPositiveButton("删除", (d,w) -> { settings.clearKeys(); status.setText("密钥已删除"); }).show());
    }
    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setPadding(0, 10, 0, 10); layout.addView(v); return v;
    }
    private EditText input(String hint, String value, boolean secret) {
        EditText v = new EditText(this); v.setHint(hint); v.setText(value); v.setSingleLine(true);
        if (secret) { v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); }
        v.setSaveEnabled(false); layout.addView(v); return v;
    }
    private void button(String label, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(action); layout.addView(b);
    }
}
