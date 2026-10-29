# Personal fork security review — 2026-10-29

## Decision and limits

Upstream reviewed: `tover0314-w/opentypeless` at **f5ad60298f535e0fff716a749802138ee409dea7** (version 1.1.64). No intentional backdoor was identified in the reviewed source paths. This is a source/dependency review, not proof that every dependency, upstream binary, provider, or future commit is safe. We did not execute an upstream release installer or audit the upstream account server. The project is suitable as a starting point **with the hardening below**; this is not an endorsement of running the unmodified release.

Scope: application startup, Tauri capabilities/CSP, Windows hotkey handling and output, audio provider routing, LLM requests, selected-text/context handling, credential migration/storage, OAuth callback validation, automatic updater, backups/history, package lifecycle scripts and lockfiles. Android is new code in this fork; it does not reuse the desktop window/hotkey subsystem.

## Findings and remediation

| Finding | Evidence / consequence | Personal fork action |
|---|---|---|
| Upstream network on launch | `App.tsx` initializes the account session; `UpdatePrompt` checks upstream releases. BYOK does not mean zero upstream requests. | Removed bootstrap and updater UI/plugin/capability/config; account API wrappers fail before fetch; account/subscription navigation hidden. Cloud configuration cannot be saved. |
| Provider prewarming without a key | Windows smoke testing and `pipeline.pre_warm` identified unauthenticated HEAD requests to the configured provider on startup/settings changes. This sends no audio, but is unnecessary background traffic. | Removed the startup/settings calls and prewarming implementation. Explicit connection tests and dictation remain network actions. |
| Upstream executable trust | Signed updater trusts the upstream release key. A fork could be replaced by a later upstream executable. | Removed the updater dependency and endpoint. Updates are manual. |
| Privacy defaults | Auto-start, transcript history and polishing default on. SQLite history contains plaintext raw/polished text. | All default off. Context adaptation also defaults off. Existing explicit user opt-ins remain explicit; history is not represented as encrypted. |
| Dependency advisories | Original npm audit: 7 affected dependency entries, including Better Auth, Vite and Vitest. Original Cargo audit: 11 vulnerability entries plus unsound/unmaintained/yanked warnings. | Updated Better Auth to 1.7.14, Vitest to 4.1.12 and compatible frontend transitive dependencies. Removed updater/zip; patched Rust TLS, anyhow, event-listener, tokio and clipboard-win versions in Cargo.lock. |
| Better Auth applicability | Critical [GHSA-965c-763c-88jm](https://github.com/advisories/GHSA-965c-763c-88jm) requires server Magic Link + OAuth conditions. This repo imports `better-auth/client`; its server implementation is not here. | Do not equate the audit severity with a demonstrated desktop account takeover. Upgrade regardless, and disable the upstream account path. |
| Provider error disclosure | Whisper and LLM HTTP failures can include provider response bodies in logs/UI errors; bodies may echo sensitive input. | Replace bodies with status plus generic diagnostic text in the direct transcription/polishing paths. |
| Redirect/destination trust | Default HTTP clients may follow redirects; user-configured remote HTTP exposes content in transit. | Shared direct-provider client disables redirects. Custom remote STT/LLM endpoints require HTTPS; HTTP is limited to loopback. Gemini validates Google's exact HTTPS host and port. |
| Credential separation | Windows uses keyring/system vault with verified legacy migration; unsupported mobile vault operations fail, rather than silently saving plaintext. | Separate app identifier and vault namespace. Android uses AES-GCM with Android Keystore, app-private preferences, backups off; no embedded API key. |

Remaining Cargo findings after fixes: `quick-xml 0.39.4` [RUSTSEC-2026-0194](https://rustsec.org/advisories/RUSTSEC-2026-0194.html) and [0195](https://rustsec.org/advisories/RUSTSEC-2026-0195.html), through the Linux Wayland proc-macro build chain. `cargo tree --target x86_64-pc-windows-msvc -i quick-xml@0.39.4` is empty. Unsoundness warnings in `glib 0.18.5` and `memmap2 0.8.0` likewise have empty Windows dependency trees. They are **not declared fixed**; Linux is outside the first release and must be reviewed before shipping. Unmaintained transitive crates remain maintenance risks, including Tauri's Unicode support dependencies. The full all-platform Cargo audit therefore still exits nonzero. The native Android application has no Rust dependency graph.

## Data and execution boundaries

- Groq receives audio and the selected term hints only when the user records and stops. Google receives the transcript only when polishing is enabled. No automatic cross-provider retry is added. Groq failures do not route audio to another provider.
- The reviewed Windows keyboard hook matches hotkey state, rather than storing/transmitting typed text. Output uses input/clipboard APIs. Voice search uses a constrained provider URL; no general shell execution interface was found. Platform helper processes in macOS/Linux are not evidence of a Windows backdoor.
- Context prompts use normalized app/category metadata; selected text is an explicit feature. Both context adaptation and selected-text capture are off by default in this distribution.
- Android IME requires the system `BIND_INPUT_METHOD` permission, microphone permission, and an explicit recording action. It cancels on hidden keyboard/editor changes; password fields disable cloud speech. Results require an Insert tap. Audio is held in memory, never recorded to history or disk by the app.
- Android raw and polished results remain available in memory until insertion/cancel/editor change. Google edits can still change meaning; no semantic guarantee is made.
- npm lockfile resolved packages are from the npm registry; the only upstream lockfile lifecycle install script found was the macOS optional `fsevents` dependency. Initial dependency installation used `--ignore-scripts`. Cargo sources are crates.io. This is not an exhaustive audit of every transitive package's source.

## Verification

Audit evidence is saved locally outside the public repository in the task's `evidence` directory: original and patched npm/Cargo JSON, build/test logs, and the Wayland inverse dependency tree. No private audio or API keys are included in this repository.

Completed initial checks: frontend build and ESLint; 491 frontend tests; Windows `cargo check`; Android APK build, Android lint (no errors), and four emulator tests including actual Keystore encryption round-trip, WAV structure, terminology bounds, and manifest protections. Additional Rust tests and interactive smoke checks are recorded in `docs/DEVELOPMENT-STATUS.md` as they complete.

No Groq/Google API key or real phone was available during this review. Recognition quality, provider-account behavior and physical-phone microphone behavior remain unverified. Android lint warns that target API 36 and the pinned build tools are not the newest; the first emulator test target is API 36. No security protection on a physical device was disabled.

Provider privacy is separate from app security. See [Groq data controls](https://console.groq.com/docs/your-data) and [Gemini API terms](https://ai.google.dev/gemini-api/terms); free and paid Google services have different data terms. Provider reputation is not an assertion of zero retention.
