# Development checkpoint — 2026-10-29

This is a personal development preview, not a production release or a claim of recognition-quality acceptance.

## Implemented

- Windows Tauri app built from the audited upstream commit, direct Groq defaults, full Whisper Large v3, UTF-8 term hints, shared conservative Gemini polish instructions, isolated app/vault identity.
- Upstream account bootstrap, updater and provider connection prewarming removed. History, auto-start, context adaptation and polishing default off. No provider HTTP redirects; remote plaintext endpoints rejected.
- Native Android voice keyboard in the same repository, API 26+, API 36 emulator tested. Explicit recording, bounded in-memory PCM, preview/Insert, raw transcript recovery, cancellation on editor/window changes, password-field exclusion, Keystore keys and backup/transfer exclusions. No embedded key or app-side audio history.
- Offline-checkable evaluation script for comparing Groq Large v3 and Turbo on identical user-provided WAV files. Upload requires an explicit command flag.

## Verified

| Check | Result |
|---|---|
| Frontend full regression suite | 491 tests passed (52 files) |
| Follow-up onboarding/auth/privacy tests | 52 tests passed after final UI/key-version changes |
| Frontend production build and ESLint | Passed |
| Windows Rust unit suite | 605 passed; 0 failed; 0 ignored |
| Rust Clippy, all targets, warnings denied | Passed |
| Android APK build and lint | Passed; remaining lint warnings concern SDK/build-tool recency and limited localization |
| Android instrumentation | 5 tests passed on API 36 emulator: encrypted credential round-trip, WAV format, term budget, manifest protections, real keyboard window/password/missing-key guards |
| Evaluator self-test | 5 offline checks passed |
| npm audit | 0 reported vulnerabilities |
| Full-platform cargo audit | Documented Linux-only quick-xml vulnerabilities and other Linux/maintenance warnings remain; see security review |

Windows smoke: executable launched, Chinese onboarding selected, account steps bypassed, Groq default and empty-key guard visible, main screen shows Groq + Gemini with polishing disabled and no account/subscription entry. A provider HEAD prewarm found during this smoke check was removed before final packaging.

## Still required

1. User-created Groq and Gemini API keys, entered on each device. No keys were available for this checkpoint.
2. Real Mandarin / mixed Chinese-English / technical vocabulary recordings and manually checked references. Measure recognition, exact names/numbers/negations, latency and polish meaning changes separately.
3. Physical Android phone tests: microphone routing, interrupted recordings, switching apps/editors, Bluetooth and OEM lifecycle behavior. The phone model has not yet been supplied and no physical device is connected.
4. Google policy/account configuration review for the intended free or paid API use, plus provider spend limits.
5. Production signing, Android distribution and Windows installer polish. Current artifacts are local development builds.

No actual Groq or Google recognition result, mobile end-to-end network success, or Typeless-equivalent quality is claimed. Local/offline inference, iOS and macOS remain later work.
