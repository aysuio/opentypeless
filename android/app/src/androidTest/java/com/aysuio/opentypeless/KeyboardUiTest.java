package com.aysuio.opentypeless;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/** Disposable-emulator UI smoke test, including the actual IME window. */
@SuppressWarnings("deprecation")
public final class KeyboardUiTest extends InstrumentationTestCase {
    private void shell(String command) throws Exception {
        try (var fd = getInstrumentation().getUiAutomation().executeShellCommand(command);
             var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] bytes = new byte[1024]; while (input.read(bytes) != -1) { }
        }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (text.contentEquals(node.getText() == null ? "" : node.getText())) return node;
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo result = find(node.getChild(i), text);
            if (result != null) return result;
        }
        return null;
    }
    private AccessibilityNodeInfo waitText(String text) {
        for (int attempt=0; attempt<80; attempt++) {
            for (var window : getInstrumentation().getUiAutomation().getWindows()) {
                var result = find(window.getRoot(), text);
                if (result != null) return result;
            }
            SystemClock.sleep(100);
        }
        fail("IME text not visible: " + text); return null;
    }
    private EditText edit(View view, String hint) {
        if (view instanceof EditText e && hint.contentEquals(e.getHint())) return e;
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) {
            EditText e = edit(group.getChildAt(i), hint); if (e != null) return e;
        }
        return null;
    }
    private void focus(Activity activity, String hint) throws Throwable {
        runTestOnUiThread(() -> {
            EditText editor = edit(activity.getWindow().getDecorView(), hint);
            assertNotNull(editor); editor.requestFocus();
            ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
        getInstrumentation().waitForIdleSync();
    }
    public void testKeyboardWindowAndPrivateFieldGuard() throws Throwable {
        var automation = getInstrumentation().getUiAutomation();
        var accessibility = automation.getServiceInfo();
        accessibility.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(accessibility);
        shell("ime enable com.aysuio.opentypeless/.VoiceImeService");
        shell("ime set com.aysuio.opentypeless/.VoiceImeService");
        shell("pm grant com.aysuio.opentypeless android.permission.RECORD_AUDIO");
        Activity activity = getInstrumentation().startActivitySync(new Intent(Intent.ACTION_MAIN)
            .setClassName("com.aysuio.opentypeless", "com.aysuio.opentypeless.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            focus(activity, "Groq API Key（留空保留现有值）");
            waitText("密码输入框已禁用云端语音");
            assertFalse(waitText("开始说话").isEnabled());
            focus(activity, "在这里测试输入（不保存）");
            assertTrue(waitText("开始说话").isEnabled());
            assertTrue(waitText("开始说话").performAction(AccessibilityNodeInfo.ACTION_CLICK));
            waitText("请先在设置填写 Groq API Key");
            assertFalse(waitText("插入文字").isEnabled());
        } finally { runTestOnUiThread(activity::finish); }
    }
}
