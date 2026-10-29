package com.aysuio.opentypeless;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Short in-memory utterances. Recording only lives while the keyboard is visible. */
final class AudioSession {
    interface Callback { void complete(byte[] pcm, String error); }
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    void stop() { running.set(false); }
    void cancel() { cancelled.set(true); stop(); }
    void start(Callback callback) {
        new Thread(() -> {
            AudioRecord recorder = null;
            byte[] result = null; String error = null;
            try {
                int size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (size <= 0) throw new Exception("麦克风不支持 16 kHz 录音");
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(size, 8192));
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new Exception("无法初始化麦克风");
                if (cancelled.get()) return;
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new Exception("无法开始录音");
                ByteArrayOutputStream pcm = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                while (running.get() && pcm.size() < 16000 * 2 * 180) {
                    int count = recorder.read(buffer, 0, buffer.length);
                    if (count < 0) throw new Exception("麦克风读取失败");
                    if (count == 0) throw new Exception("麦克风未返回音频");
                    pcm.write(buffer, 0, count);
                }
                result = pcm.toByteArray();
                if (result.length < 3200) throw new Exception("录音太短，请重新录音");
            } catch (SecurityException e) { error = "请在设置中授予麦克风权限";
            } catch (Exception e) { error = e.getMessage();
            } finally {
                if (recorder != null) {
                    try { recorder.stop(); } catch (IllegalStateException ignored) { }
                    recorder.release();
                }
                running.set(false);
            }
            if (!cancelled.get()) callback.complete(result, error);
        }, "opentypeless-microphone").start();
    }
}
