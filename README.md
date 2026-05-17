# FirstVoice 🌍🗣️

**Offline Multilingual Crisis Communication Agent — Powered by Gemma 4**

[![Kaggle](https://img.shields.io/badge/Kaggle-Gemma%204%20Good%20Hackathon-blue)](https://www.kaggle.com/competitions/gemma-4-good-hackathon)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Model](https://img.shields.io/badge/Model-Gemma%204%20E4B-orange)](https://ollama.com/library/gemma4:e4b)

> When disasters strike, communication infrastructure collapses. FirstVoice turns any phone into an AI-powered bridge between responders and survivors who don't share a common language — completely offline.

---

## 🎬 Demo

- **Video Demo**: [YouTube Link] *(3 min)*
- **Live Demo**: [Kaggle Notebook / Gradio Link]
- **Tracks**: Main Track · Global Resilience · Ollama Prize

---

## ✨ What It Does

| Feature | How It Works |
|---------|-------------|
| 🎤 **Speech → Text** | Push-to-talk recording → Gemma 4 E4B native audio encoder (16kHz WAV) |
| 🌐 **Language Detection** | Automatic detection from speech in 140+ languages |
| 🔄 **Translation** | Bidirectional real-time translation via Gemma 4 |
| 🔊 **Speak Back** | Android TTS reads translation aloud in survivor's language |
| 📸 **Vision Assessment** | Photo → Gemma 4 vision encoder → structural damage/injury/hazard ratings |
| 📋 **AI Triage Cards** | Gemma 4 native function calling → structured JSON (urgency, needs, GPS) |
| 📡 **BLE Mesh Sync** | Devices share triage cards via Bluetooth — no server needed |
| 🎙️ **Walkie-Talkie** | Voice clips broadcast over mesh network |
| 💬 **Quick Phrases** | 30+ pre-translated emergency phrases, instant playback |
| 🗺️ **Offline Dashboard** | Map + timeline of all incidents, color-coded by urgency |

**Everything runs offline. Zero internet required.**

---

## 🧠 Gemma 4 Features Used

| Gemma 4 Capability | Our Usage |
|---|---|
| **Native Audio Encoder** (300M params) | Direct speech-to-text, no separate ASR model |
| **Native Vision Encoder** | Damage/injury assessment from photos |
| **Native Function Calling** (tools API) | Structured triage card generation |
| **140+ Languages** | Multilingual transcription & translation |
| **128K Context Window** | Full encounter history per session |
| **System Role** | Structured agent prompts |
| **Apache 2.0 License** | Free deployment for any organization |

**Model:** `gemma4:e4b` (9.6GB, 4.5B effective params) via Ollama

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| AI Model | Gemma 4 E4B via Ollama |
| Android App | Kotlin + Jetpack Compose |
| Local Storage | Room (SQLite) |
| Mesh Networking | BLE (GATT server/client) |
| Audio | Android AudioRecord (16kHz PCM16 → WAV) |
| TTS | Android TextToSpeech (offline) |
| Offline Maps | Leaflet.js + pre-cached OSM tiles |
| Web Demo | Kaggle Notebook + Gradio |

---

## 🚀 Quick Start

### Prerequisites
- macOS/Linux with [Ollama](https://ollama.com) installed
- Android device (Android 10+)
- USB cable + ADB

### Setup

```bash
# 1. Pull Gemma 4 E4B
ollama pull gemma4:e4b

# 2. Start Ollama
OLLAMA_HOST=0.0.0.0 ollama serve

# 3. Connect phone via USB and forward port
adb reverse tcp:11434 tcp:11434

# 4. Build and install
cd android
./gradlew installDebug

# 5. In app Settings: set Ollama URL to http://localhost:11434
```

### Run Web Demo
```bash
# Open in Kaggle or Google Colab
# See packages/web/firstvoice_demo.ipynb
```

---

## 📁 Project Structure

```
firstvoice/
├── android/                         # Native Android app
│   └── app/src/main/java/com/firstvoice/app/
│       ├── ai/                      # AI engines
│       │   ├── OllamaClient.kt     # Ollama API (tools, audio, vision)
│       │   ├── SpeechEngine.kt     # Audio → Gemma 4 transcription
│       │   ├── TranslationEngine.kt # Language detection + translation
│       │   ├── VisionAnalyzer.kt   # Image → damage/injury assessment
│       │   ├── TriageAgent.kt      # Function calling → triage cards
│       │   └── PromptTemplates.kt  # All Gemma 4 prompts
│       ├── sync/
│       │   └── BleMeshSyncService.kt # BLE peer discovery + GATT sync
│       ├── ui/screens/              # Jetpack Compose UI
│       ├── data/                    # Room DB entities + DAOs
│       └── util/                    # AudioRecorder, CameraHelper
├── packages/web/                    # Kaggle Colab + Gradio demo
├── data/
│   └── quick-phrases.json           # Pre-translated emergency phrases
├── docs/
│   ├── writeup.md                   # Kaggle writeup (≤1500 words)
│   └── demo-script.md              # Video demo script
├── LICENSE                          # Apache 2.0
└── README.md
```

---

## 📊 Performance (Measured)

| Operation | Latency | Notes |
|-----------|---------|-------|
| Audio transcription | ~10-18s | 5s audio clip on Mac M4 via ADB |
| Translation | ~1-2s | Single Gemma 4 call |
| Triage card generation | ~7s | Function calling, think:false |
| Vision analysis | ~5-10s | Depends on image complexity |
| BLE mesh sync | ~5s | Per peer, manifest-based |

---

## 🌍 Impact

**Target deployments:**
- Disaster response (earthquakes, floods, cyclones)
- Refugee camp registration (50+ language groups)
- Mass casualty incidents
- Military operations in foreign deployments
- Pandemic field teams in rural areas

**Cost:** One Android phone ($200) + free model (Apache 2.0). No servers, no subscriptions, no vendor lock-in.

---

## 📄 License

Apache 2.0 — consistent with Gemma 4's licensing. Any government, NGO, or military can deploy freely.

---

## 🏆 Hackathon

Built for the [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon) on Kaggle.

**Prize Pool:** $200,000 USD  
**Tracks:** Main Track · Global Resilience · Ollama Prize
