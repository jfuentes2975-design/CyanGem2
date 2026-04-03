# CyanGem 💎

Personal Android app to control **Hey Cyan smart glasses** with **native Google Gemini AI** — no Tasker, no third-party automation.

Built for Galaxy S26 Ultra (Android 15). Personal use only.

---

## Features

| Feature | Status |
|---------|--------|
| BLE scan & connect to Hey Cyan glasses | ✅ |
| Photo / video / audio capture control | ✅ |
| Battery monitoring | ✅ |
| Media sync via Wi-Fi Direct → Gallery | ✅ |
| Native Gemini 2.0 Flash (text chat) | ✅ |
| Native Gemini Vision (photo from glasses) | ✅ |
| Streaming responses | ✅ |
| Gems — custom AI personas | ✅ (6 built-in + create your own) |
| API key stored encrypted on-device | ✅ |

---

## Setup

### 1. Get a Gemini API Key (free)

1. Go to **https://aistudio.google.com**
2. Sign in with your Google account
3. Click **Get API key** → **Create API key**
4. Copy it

### 2. Build & Install

**Requirements:**
- [Android Studio](https://developer.android.com/studio) (Hedgehog or newer)
- Android SDK 35
- Physical Android device (BLE won't work on emulator)

**Steps:**
```bash
# Clone or unzip this project
cd CyanGem

# Open in Android Studio → File → Open → select this folder
# Or build from command line:
./gradlew assembleDebug

# Install:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. First-time app setup

1. Open **CyanGem** on your S26 Ultra
2. Go to **Settings** tab → paste your Gemini API key → **Save Key**
3. Go to **Glasses** tab → tap **Scan for Glasses**
4. Turn on your Hey Cyan glasses (hold power button until LED blinks)
5. Tap your glasses in the list to connect

---

## Using Gemini with Your Glasses

### Text chat
- Go to **Chat** tab → type anything → send

### Analyze what your glasses see
1. Make sure glasses are connected (BLE)
2. Take a photo with your glasses (tap Photo in Glasses tab)
3. Tap **📷 Analyze glasses photo** in the Chat tab
4. The app downloads the latest photo via Wi-Fi Direct and sends it to Gemini

### Auto-trigger (AI button on glasses)
If your glasses have an AI button, pressing it sends a `0x06` BLE command.
CyanGem intercepts this and **automatically** downloads the latest photo and routes it to Gemini — no tapping required.

### Gems
- Go to **Gems** tab to pick an AI persona (Navigator, Translator, Visual Describer, etc.)
- Tap **Use** to activate — the Chat tab switches to that persona
- Tap **+** to create your own Gem with any custom system prompt

---

## Media Sync

1. Connect to glasses via BLE first
2. Go to **Glasses** tab → tap **Sync Media to Gallery**
3. The app connects to the glasses' Wi-Fi Direct hotspot and downloads all photos/videos/audio
4. Files saved to `DCIM/CyanGem` in your Gallery

> **Note:** Your phone may briefly lose internet during sync since it joins the glasses' Wi-Fi network.

---

## BLE Troubleshooting

### Commands not working?

The BLE command bytes and characteristic UUIDs are reverse-engineered from the public QCSDK.
They should work, but if they don't:

1. Install **nRF Connect** from Google Play
2. Connect to your glasses in nRF Connect
3. Note the service UUIDs and characteristic UUIDs
4. Compare against what **BLE Inspector** shows in the Glasses tab
5. If different, update `BleConstants.kt`:

```kotlin
val CHAR_WRITE  = UUID.fromString("YOUR-ACTUAL-WRITE-CHAR-UUID")
val CHAR_NOTIFY = UUID.fromString("YOUR-ACTUAL-NOTIFY-CHAR-UUID")
```

Then rebuild: `./gradlew assembleDebug && adb install -r ...`

### Known Service UUIDs (confirmed)
```
Primary:   7905FFF0-B5CE-4E99-A40F-4B1E122D00D0
Secondary: 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e
```

### Battery optimization (important)
Android will kill the BLE connection in background.
Go to: **Settings → Apps → CyanGem → Battery → Unrestricted**

---

## Project Structure

```
app/src/main/java/com/cyangem/
├── MainActivity.kt              # Entry point, permissions
├── ble/
│   ├── BleConstants.kt         # UUIDs, command bytes, protocol helpers
│   ├── CyanBleManager.kt       # BLE scan/connect/command engine
│   └── BleConnectionService.kt # Foreground service for background BLE
├── gemini/
│   ├── GeminiEngine.kt         # Gemini text + vision client
│   └── GemsRepository.kt       # Gems (personas) storage & built-ins
├── media/
│   └── MediaSyncManager.kt     # Wi-Fi Direct HTTP download + Gallery save
├── data/
│   └── ApiKeyStore.kt          # Encrypted API key storage
├── viewmodel/
│   └── MainViewModel.kt        # Central state + business logic
└── ui/
    ├── CyanGemApp.kt           # Nav host + bottom bar
    ├── GlassesScreen.kt        # BLE controls, media sync, status
    ├── ChatScreen.kt           # Gemini chat + streaming
    ├── GemsScreen.kt           # Gems browser + editor
    └── SettingsScreen.kt       # API key, tips, protocol info
```

---

## What's Verified vs. Inferred

| Item | Status |
|------|--------|
| Primary Service UUID | ✅ Confirmed (public SDK) |
| Secondary Service UUID | ✅ Confirmed (public SDK) |
| CHAR_WRITE UUID (`7905FFF1-...`) | ⚠️ Inferred — verify with nRF Connect |
| CHAR_NOTIFY UUID (`7905FFF2-...`) | ⚠️ Inferred — verify with nRF Connect |
| Command bytes (photo, video, etc.) | ⚠️ Reverse-engineered from iOS SDK enum |
| Media config format | ✅ Confirmed (CyanBridge v1.0.2 notes) |
| Wi-Fi IP candidates | ✅ Confirmed (WIFI_TRANSFER_ARCHITECTURE.md) |

---

## Gemini API Cost

Using `gemini-2.0-flash`. As of 2025, Google offers:
- **Free tier**: 15 requests/min, 1M tokens/day
- Paid starts at ~$0.075 / 1M input tokens

For personal use with smart glasses, free tier is more than enough.

---

*CyanGem is a personal-use project. Not affiliated with Hey Cyan or Anthropic.*
