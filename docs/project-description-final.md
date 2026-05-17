# FirstVoice — Project Description (Kaggle Submission)

## The Problem We Must Address

On January 26, 2001, a **7.7 magnitude earthquake** struck Gujarat, India. **20,000 people died.** International rescue teams arrived within hours — but couldn't communicate with trapped survivors speaking Gujarati, Kutchi, and Sindhi. *People died not because help didn't arrive, but because help couldn't talk.*

This repeats in every major disaster. Turkey-Syria 2023. Morocco 2023. Myanmar 2024. When infrastructure collapses, communication collapses with it. First responders arrive in areas where populations speak dozens of languages. Survivors are injured, panicked, often illiterate. Cell towers are down. Internet is gone. Interpreters are unavailable. Critical triage information — "I'm diabetic," "my child is under the building," "there's a gas leak" — gets lost.

## What FirstVoice Does

**FirstVoice turns any Android phone into an AI-powered communication bridge** between disaster responders and affected populations who don't share a common language. **It works completely offline.**

A responder taps the mic, speaks in English: *"Are you injured? Can you move your legs?"*

Gemma 4 transcribes the audio, detects the language, translates it, and the phone speaks it back in the survivor's language — Bengali, Hindi, Arabic, whatever was detected from their first utterance. The survivor responds. Gemma 4 transcribes, translates, displays. **A full bidirectional conversation happens without internet.**

## Core Features (All Working, All Offline)

- **🎤 Push-to-Talk Audio → Gemma 4 Transcription** — Records audio (16kHz mono WAV), sends directly to Gemma 4 E4B's native audio encoder for transcription. No Android SpeechRecognizer, no internet dependency.
- **🔄 Automatic Language Detection + Translation** — Single Gemma 4 call detects language and translates. Tested with English, Hindi, Bengali, Spanish.
- **🔊 TTS Playback** — Tap speaker icon on any message to hear it spoken aloud in the target language using Android's offline TTS.
- **📸 Vision Assessment** — Photograph structural damage, injuries, or hazards. Gemma 4's vision encoder analyzes the image and produces structured severity ratings.
- **📋 AI Triage Cards via Function Calling** — After each encounter, Gemma 4 generates a structured triage card using native function calling (Ollama tools API). Outputs: urgency level, people count, needs categories, language, GPS, summary.
- **📡 BLE Mesh Sync** — Multiple responder phones discover each other via Bluetooth Low Energy and automatically share triage cards. No pairing dialogs, no server.
- **🎙️ Voice Walkie-Talkie** — Tap-to-record audio clips broadcast over the mesh network.
- **💬 Quick Phrases** — 30+ pre-translated emergency phrases for instant communication without AI delay.
- **🗺️ Offline Dashboard** — All incidents on a pre-cached map, color-coded by urgency.

## How We Use Gemma 4

**Model:** Gemma 4 E4B (4.5B effective parameters, 8B with embeddings, 9.6GB via Ollama)

**1. Native Audio Encoder (300M params)**
Gemma 4 E4B has a built-in audio encoder that processes speech directly. We record 16kHz mono PCM audio, wrap it in a WAV header, base64 encode it, and send it via Ollama's multimodal API. The model transcribes speech and detects language in a single inference call. *Each second of audio = 25 tokens.*

**2. Native Vision Encoder**
The same model analyzes photos of collapsed buildings, injuries, and hazards. We send base64 JPEG images via Ollama's API with structured prompts requesting severity ratings and hazard identification.

**3. Native Function Calling (via Ollama Tools API)**
For triage card generation, we define a `generate_triage_card` tool with a JSON schema. Gemma 4 responds with structured `tool_calls` containing the arguments — no regex parsing, no brittle JSON extraction.

**4. 140+ Language Support**
Gemma 4's multilingual training enables transcription and translation across languages without separate models per language pair.

**5. 128K Context Window**
The full multi-turn encounter fits in a single context, so the triage card captures the complete picture.

**6. System Role Support**
Gemma 4 natively supports the `system` role for structured agent behavior.

## Technical Architecture

**Android App** (Kotlin / Jetpack Compose) → **OllamaClient** (NDJSON, tools support) → **Gemma 4 E4B** via Ollama (localhost:11434) → **Room DB** + **BLE Mesh Sync** + **Android TTS**

*Speech Pipeline:* AudioRecorder (16kHz mono PCM16) → WAV header → base64 → Gemma 4 audio encoder → transcription + language detection → Gemma 4 translation → TTS playback.

*Triage Pipeline:* Full conversation context → Gemma 4 with `tools` parameter → model returns `tool_calls` → structured TriageCard → Room DB → BLE mesh broadcast.

## Real-World Testing

Tested on **physical Android devices** (OPPO CPH2401, Android 14) connected to Mac M4 running Ollama via USB ADB reverse port forwarding:

- ✅ English audio transcription: Working, HIGH confidence
- ✅ Hindi/Bengali detection: Language correctly identified
- ✅ Triage card generation: **7 seconds** via function calling
- ✅ Vision analysis: Structural damage assessed with severity ratings
- ✅ BLE mesh sync: Triage cards synced between two devices
- ✅ Fully offline: **Zero network calls** after model download

## Impact & Scalability

**Deployment cost:** One $200 Android phone + Gemma 4 E4B (free, Apache 2.0). *No servers. No subscriptions.*

**Target scenarios:**
- NDRF/SDRF disaster response across India's 22 official languages
- UNHCR refugee registration in camps with 50+ language groups
- Mass casualty incidents where interpreters are unavailable
- Military CASEVAC in foreign deployments
- Pandemic field teams in rural areas with no connectivity

**What makes this different from cloud translation apps:**
1. Works when cell towers are down *(the exact moment it's needed most)*
2. Mesh relay builds shared situational awareness without any server
3. AI triage produces structured actionable data, not just translated words
4. Single model handles audio + vision + translation + structured output
5. Apache 2.0 — any government or NGO can deploy without barriers

---

***FirstVoice doesn't just translate words. It saves the information that saves lives.***
