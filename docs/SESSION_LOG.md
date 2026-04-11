# CyanGem — Master Session Log
> Newest session at top. Claude reads this file at every "Begin Session" prompt.
> Stored at: `docs/SESSION_LOG.md` in `github.com/jfuentes2975-design/CyanGem2`

---

## SESSION LOG — April 11, 2026
**Completed:**
- Diagnosed OpenRouter "No Endpoint found" root cause: HTTP 200 with error body bypassed isSuccessful check, threw JSONException, broke fallback loop
- Fixed callApi() to parse JSON body first, extract error field before HTTP status check, use null-safe opt* accessors
- Added model fallback lists: 4 vision models, 4 text models — auto-retries on endpoint failure
- Added syncAllMedia() to CyanBleManager (was in zip deliveries but never in GitHub repo)
- Wired syncMedia() in MainViewModel to use full BLE multi-photo sync with progress
- Ran full pre-release validation: brace/paren balance, logic regression check, thread safety, auth fast-fail
- Fixed two issues found in validation: readTimeout 60s→15s (240s worst case → 60s), GlassesScreen Wi-Fi Direct subtitle regression
- All 5 files validated clean — committed b8acdf5

**Decisions made:**
- readTimeout 15s per model × 4 models = 60s max wait — acceptable for voice UX
- GlassesScreen subtitle permanently fixed to BLE language, not Wi-Fi
- Pre-release validation gate to run before every build going forward

**Open items:**
- AI chat/voice response still untested after this fix — user building and testing
- BLE photo transfer (sync, auto-save on PhotoTaken) still unverified on live W610
- Photos count increases on glasses but nothing saves to gallery — BLE transfer pipeline unconfirmed
- Chat history persistence (Sprint 1.2) not built
- Onboarding, global status, API key validation (Sprints 1.3–1.5) not built
- GitHub token expires April 13 2026

**Next step:** User tests build — confirm (1) chat responds after typing, (2) voice gives audio answer after "Hey Cyan", (3) Photo button shows snackbar. Report results before any new code.

**My preferences noted:**
- Sparring partner mode — find blindspots, tell the truth, no filler
- Findings before fixes, no coding before planning
- Run pre-release validation before every build
- Push directly to GitHub, replace only changed files
- Direct and technical, call out uncertainty explicitly

**Key files/links:**
- Repo: github.com/jfuentes2975-design/CyanGem2 (private)
- Session log: github.com/jfuentes2975-design/CyanGem2/blob/main/docs/SESSION_LOG.md
- GitHub token: expires April 13 2026
- Latest commits: eca02b9 (error parsing), 5948492 (fallbacks + BLE sync), b8acdf5 (validation fixes)
- Sprint tracker: docs/CyanGem_Product_Enhancement_Report.md

---

## SESSION LOG — April 10, 2026 (Session 2)
**Completed:**
- Fixed voice pipeline Bug 1: onPartialResults calling startListening() while recognizer active → ERROR_RECOGNIZER_BUSY → silent death. Fixed with wakeWordHandled flag + stopListening() before onWakeWord invoke
- Fixed voice pipeline Bug 2: No audio cue after wake word. Now speaks "Yes?" via TTS then 700ms delay before startListening()
- Fixed voice pipeline Bug 3: TTS dropping first speak() call — async init race. Added pendingSpeakQueue flushed when ttsReady fires
- Confirmed "Yes?" tone IS playing after "Hey Cyan" — TTS + BT audio routing confirmed working
- Confirmed BLE genuinely connected (photo count 11, video 2, audio 6 loaded correctly)
- Identified phone silent mode icon visible in screenshots — possible media volume issue

**Decisions made:**
- One file only changed: VoiceEngine.kt — commit 6e3f148
- Fresh install required due to signing mismatch on update
- Diagnostic next step before any more code: check Chat tab for messages after voice query + check Photo button snackbar

**Open items:**
- Voice query pipeline still broken AFTER wake word — hear "Yes?" but no AI response and no audio
- Root cause unknown: could be OpenRouter not responding, TTS not speaking response, or permissions on fresh install
- Need user to check: (1) Chat tab after voice query — any messages? (2) Photo button — any snackbar?
- Media volume may be at zero despite BT audio working for tone — needs verification
- Photo button via BLE controls untested — no snackbar reported means BLE command path may be broken
- Chat history persistence (Sprint 1.2) not built
- Onboarding, global status, API key validation (Sprints 1.3–1.5) not built

**Next step:** User tests: tap Chat after voice query (any messages?), tap Photo button (any snackbar?), check media volume. Report back before any code changes.

**My preferences noted:**
- Sparring partner mode — find blindspots, tell the truth, no filler
- Findings before fixes, no coding before planning
- Push directly to GitHub, replace only changed files
- Direct and technical, call out uncertainty explicitly

**Key files/links:**
- Repo: github.com/jfuentes2975-design/CyanGem2 (private)
- Session log: github.com/jfuentes2975-design/CyanGem2/blob/main/docs/SESSION_LOG.md
- GitHub token: expires April 13 2026
- Latest commit: 6e3f148 (VoiceEngine fix)
- OpenRouter key: re-entered after fresh install

---

## SESSION LOG — April 10, 2026
**Completed:**
- Built OpenRouterEngine.kt — free AI via llama-3.2-11b-vision-instruct:free, no billing, uses existing OkHttp
- Built in-app photo gallery (GalleryScreen.kt) — grid, tap-to-expand, Ask Gemini per photo, Coil 2.7.0
- Updated ApiKeyStore, MainViewModel, SettingsScreen for dual-provider support (OpenRouter default / Gemini fallback)
- Pushed all changes directly to GitHub via token — commits 9f6f2b8, a4916f1, a76f3f6
- Created master session log at docs/SESSION_LOG.md — auto-updated at End Session, auto-read at Begin Session

**Decisions made:**
- OpenRouter is the new default free AI provider — Gemini kept as optional paid fallback
- Session log lives in GitHub repo — Claude pulls and pushes it each session
- GitHub token expires April 13 — user chose not to revoke early

**Open items:**
- Wake word "Hey Cyan" triggers recognition but nothing happens after — voice query pipeline broken post-wake
- No TTS audio response — voice output not firing despite BT audio confirmed working (music plays fine)
- BLE photo transfer untested end-to-end on live W610
- Chat history persistence (Sprint 1.2) not built
- Onboarding, global status, API key validation (Sprints 1.3–1.5) not built

**Next step:** Diagnose voice pipeline — wake word fires but onResult/sendMessage chain is breaking; check VoiceEngine.onResult callback wiring and TTS speak() in MainViewModel; check if OpenRouter response returns but TTS silently fails

**My preferences noted:**
- Sparring partner mode — find blindspots, tell the truth, no filler
- Findings before fixes, no coding before planning
- Push directly to GitHub, replace only changed files
- Direct and technical, call out uncertainty explicitly

**Key files/links:**
- Repo: github.com/jfuentes2975-design/CyanGem2 (private)
- Session log: github.com/jfuentes2975-design/CyanGem2/blob/main/docs/SESSION_LOG.md
- GitHub token: expires April 13 2026
- OpenRouter key: saved in app Settings
- Sprint tracker: docs/CyanGem_Product_Enhancement_Report.md

---

## SESSION LOG — April 10, 2026
**Completed:**
- Built OpenRouterEngine.kt — free AI alternative using llama-3.2-11b-vision-instruct:free via OpenRouter REST API (no new dependencies, uses existing OkHttp)
- Updated ApiKeyStore to store OpenRouter key + provider preference alongside Gemini key
- Updated MainViewModel with `activeEngine` property routing to OpenRouter or Gemini based on stored preference
- Updated SettingsScreen with provider toggle (OpenRouter recommended / Gemini paid) + OpenRouter key entry field
- Built in-app photo gallery (GalleryScreen.kt) — 3-col grid, tap-to-expand, Ask Gemini per photo, auto-refreshes after sync

**Decisions made:**
- OpenRouter with llama-3.2-11b-vision-instruct:free is the new default AI provider — free, no billing, vision-capable
- Gemini kept as optional provider for users who want it — both engines initialize if keys present
- Gallery added as 5th nav tab between Gems and Settings using Coil 2.7.0

**Open items:**
- Build not compiled or tested since gallery + OpenRouter changes
- Chat history persistence (Sprint 1.2) not yet built
- First-run onboarding (Sprint 1.3) not yet built
- Global connection status indicator (Sprint 1.4) not yet built
- API key validation on save (Sprint 1.5) not yet built
- BLE photo transfer still untested on live W610 hardware

**Next step:** Build Sprint 1.2 — chat history persistence using DataStore + Gson (already in project), seed Gemini/OpenRouter context on restore, cap at 50 messages

**My preferences noted:**
- Sparring partner mode — find blindspots, tell the truth, no filler
- No coding before findings/planning (Findings Before Fixes)
- Direct and technical, call out uncertainty explicitly
- Push changes directly to GitHub — token active until April 13
- Replace only changed files, not whole project

**Key files/links:**
- Repo: github.com/jfuentes2975-design/CyanGem2 (private)
- GitHub token: active, expires April 13 2026
- OpenRouter key: stored in app Settings (not in source)
- Commits this session: `9f6f2b8` (gallery), `a4916f1` (OpenRouter engine)
- Sprint plan: docs/CyanGem_Product_Enhancement_Report.md in repo

---

## SESSION LOG — April 9, 2026 (Session 3)
**Completed:**
- Analyzed APK app-debug_9_9_26.apk — confirmed BLE sync code present (syncAllMedia, fetchLatestPhoto, PhotoReceived all in DEX)
- Researched competitive landscape: Meta AI app, Solos AirGo V, Halliday, Snap Spectacles, Android XR ecosystem
- Produced full product enhancement report (CyanGem_Product_Enhancement_Report.md) — 16 features across 4 sprints
- Identified onboarding + API key validation as highest-priority gaps
- Identified Gems system as strongest competitive differentiator

**Decisions made:**
- Sprint 1 order: gallery → chat persistence → onboarding → global status → API key validation
- Live translation is Sprint 2 — parity feature, not blocking
- Gem Marketplace flagged as long-term platform moat

**Open items:**
- BLE photo transfer untested on live W610
- Gemini key cost issue — user forced onto paid tier
- Video sync blocked by SDK vendor
- iOS port does not exist
- Target user persona not defined

**Next step:** Test BLE on live W610, check GLASSES_LOG logcat

**My preferences noted:**
- Sparring partner mode, no filler, findings before fixes
- Replace only changed files

**Key files/links:**
- Delivered: CyanGem_Product_Enhancement_Report.md
- APK analyzed: app-debug_9_9_26.apk (v1.0.0 debug)

---

## SESSION LOG — April 9, 2026 (Session 2)
**Completed:**
- Built full multi-photo BLE sync using CyanBleManager.syncAllMedia()
- SDK handles iteration internally via getPictureThumbnails() — no external loop needed
- Wired real-time progress bar in MainViewModel.syncMedia()
- Fixed GlassesScreen subtitle from "Wi-Fi Direct" to "Syncs photos via Bluetooth"
- Added duplicate-sync guard
- Delivered CyanGem2-sync.zip (3 files: CyanBleManager.kt, MainViewModel.kt, GlassesScreen.kt)

**Decisions made:**
- Photos only — video sync not possible, LargeDataHandler has no video transfer method
- SDK iterates photos internally, isLast=true signals end
- Progress denominator uses coerceAtLeast(current) for stale count handling

**Open items:**
- syncAllMedia() untested on live W610
- Video sync blocked by SDK
- fetchLatestPhoto() untested
- Gemini API key fix pending

**Next step:** Build + install, test on W610, check GLASSES_LOG logcat

**My preferences noted:**
- Sparring partner, no filler, findings before fixes, replace only changed files

**Key files/links:**
- Repo: github.com/jfuentes2975-design/CyanGem2 (private)
- Delivered: CyanGem2-sync.zip

---

## SESSION LOG — April 8, 2026 (Session 1)
**Completed:**
- Full APK reverse-engineering of app-debug__8_.apk
- Confirmed lazy init fixed, VoiceEngine order fixed, CyanBleReceiver partially fixed
- Diagnosed Gemini quota error: limit=0 on free-tier = wrong API key project
- Confirmed W610 glasses are BLE-only — Wi-Fi HTTP sync was wrong transport
- Mapped complete BLE photo pipeline from SDK bytecode: onCharacteristicChange → LargeDataParser.parseBigLargeData() → ILargeDataImageResponse.parseData(index, isLast, bytes)
- Delivered CyanGem2-fixed.zip (4 files)

**Decisions made:**
- BLE is only transport for W610 — Wi-Fi sync code preserved but deprecated
- Photos auto-save to DCIM/CyanGem via MediaStore on PhotoTaken event
- fetchLatestPhoto() callbacks returned on main thread via mainHandler.post()

**Open items:**
- fetchLatestPhoto() untested on live W610
- TikTok/Instagram streaming — platform limitation confirmed, no viable path
- Build not verified

**Next step:** Build + install, test BLE photo transfer on W610

**My preferences noted:**
- Sparring partner, no filler, findings before fixes, replace only changed files

**Key files/links:**
- Delivered: CyanGem2-fixed.zip (4 files changed)
- APK analyzed: app-debug__8_.apk (v1.0.0 debug)
- Gemini fix: aistudio.google.com/app/apikey → billing-enabled project

---

## Project State Reference (always current)

### Hardware
- Glasses: W610, BLE-only (no Wi-Fi)
- Phone: Galaxy S26 Ultra
- SDK: glasses_sdk_20250723_v01.aar (com.oudmon.ble)

### Architecture
- Single-activity Jetpack Compose, MVVM
- MainViewModel lazy-initializes all subsystems
- 5 nav tabs: Glasses, Chat, Gems, Gallery, Settings
- BLE pipeline: onCharacteristicChange → LargeDataParser → ILargeDataImageResponse → fetchLatestPhoto/syncAllMedia

### Key Technical Facts
- Photo transfer: LargeDataHandler.getPictureThumbnails() → ILargeDataImageResponse.parseData(cmdType, isLast, bytes)
- Video sync: NOT POSSIBLE — no LargeDataHandler video transfer method in current SDK
- Photos save to: DCIM/CyanGem via MediaStore
- AI: OpenRouter (default, free) + Gemini (optional, paid) — swappable in Settings
- Wake words: hey cyan, hey gem, hey gemini, cyan, ok cyan

### Files Changed History
| Commit | Files | Description |
|--------|-------|-------------|
| a4916f1 | OpenRouterEngine.kt, ApiKeyStore.kt, MainViewModel.kt, SettingsScreen.kt | Free AI provider via OpenRouter |
| 9f6f2b8 | GalleryScreen.kt, CyanGemApp.kt, MainViewModel.kt, libs.versions.toml, build.gradle.kts | In-app photo gallery |
| Prior | CyanBleReceiver.kt, CyanBleManager.kt, MainViewModel.kt, MediaSyncManager.kt, GlassesScreen.kt | BLE photo transfer + multi-sync |

### Sprint 1 Status
- [x] 1.1 In-app photo gallery
- [ ] 1.2 Chat history persistence
- [ ] 1.3 First-run onboarding
- [ ] 1.4 Global connection status
- [ ] 1.5 API key validation
