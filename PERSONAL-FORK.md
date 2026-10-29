# OpenTypeless Personal — Windows + Android

Based on the MIT-licensed OpenTypeless desktop source; upstream attribution and license are retained. This fork adds a native Android voice input method alongside the Tauri Windows app. The Android UI and microphone/credential layer are native Java; the conservative polishing instructions are shared in `shared/polish-instructions.txt`.

Read the [security review](docs/SECURITY-REVIEW-2026-10-29.md) before evaluating this development build.

## First version

- Windows: existing hotkey recording and insertion workflow; default Groq `whisper-large-v3`, multilingual transcription, term dictionary supplied as a bounded Whisper prompt.
- Android 8+ (API 26+): enable the voice keyboard in Android settings, grant microphone permission, then select it in an input field. Tap Start, Stop, review, Insert. A keyboard-switch button returns to the regular typing keyboard. Recording is limited to three minutes and cancels when the keyboard hides or the editor changes.
- Optional Google Gemini text polishing, currently defaulting to `gemini-3.8-flash`; model is configurable. It is off by default. Android retains the raw transcript for review. The app does not promise that an LLM preserves meaning perfectly.
- Direct personal API keys only. No upstream account subscription, automatic updater, telemetry, or default transcript history. Windows keys use Windows Credential Manager; Android keys use Android Keystore encryption. Set provider spend limits in their consoles.

Groq receives audio; Google receives text when enabled. This is **not offline mode**. No provider key is shipped or stored in source control. Do not paste real keys into issues or test fixtures.

## Build

Windows: Node 24, Rust stable, MSVC C++ build tools and WebView2. Run `npm ci --ignore-scripts`, `npm test`, and `npm run tauri build -- --debug --no-bundle` for a development executable. Production code signing is not configured.

Android: JDK 17+, Android SDK platform 36, then `cd android` and `gradlew.bat :app:assembleDebug`. The wrapper distribution has a pinned SHA-256. The debug APK is under `android/app/build/outputs/apk/debug/app-debug.apk`. It is for personal testing, not a signed store release. No release signing key is in the repo.

Checks: `npm run build`, `npm run lint`, `npm test`; `cargo fmt --check`, `cargo test --lib` and `cargo clippy --all-targets` from `src-tauri`; `gradlew.bat :app:lintDebug :app:connectedDebugAndroidTest` with a disposable Android emulator. A complete all-platform Cargo audit reports documented Linux-only transitive findings; do not hide them with an unexplained blanket ignore.

## Quality acceptance still needed

Use the same recordings on both platforms and compare raw recognition before evaluating polishing. Include ordinary Mandarin, English acronyms, mixed technical sentences, names/identifiers, dates/amounts/units, negation, corrections, accents and moderate background noise. Track character/word error rate, exact technical-term and number accuracy, end-of-speech latency, and meaning-changing edits separately. Do not accept better punctuation as evidence of better recognition.

No real API recognition or user-speech benchmark has passed yet: keys are not available. Physical Android device testing is also pending. Initial builds are engineering previews, not a claim of Typeless-equivalent quality.

`python scripts/evaluate-groq.py --self-test` checks the evaluator without a network request. For real evaluation, keep WAV files and a manifest in the git-ignored `.private/` folder, set `GROQ_API_KEY` in the local process environment, then explicitly run `python scripts/evaluate-groq.py .private/samples.json --send-to-groq`. This uploads each sample once to each selected model (full v3 and Turbo by default) and writes private transcripts, character error rates, term checks and request latency to `.private/evaluation.json`. Review negation, numbers and meaning manually.
