# FirstVoice — Video Demo Script (3 Minutes Max)

---

## PRE-RECORDING CHECKLIST
- [ ] Ollama running with gemma4:e4b loaded
- [ ] Phone connected, adb reverse set up
- [ ] App freshly installed, no old data
- [ ] Second phone ready for mesh sync demo
- [ ] Screen recording on both phones (or scrcpy on Mac)
- [ ] Quiet room for audio demo
- [ ] A disaster photo ready (building damage/rubble)

---

## [0:00–0:25] OPENING — The Problem + Intro (25s)

**Screen:** Title card or text overlay on disaster footage

**Voiceover (you speaking to camera or just voice):**

> "In every disaster — earthquakes, floods, cyclones — rescue teams arrive but can't communicate with survivors who speak different languages. No internet. No interpreters. Critical information gets lost. People die from miscommunication.

> FirstVoice solves this. It's an Android app powered by Gemma 4 that enables real-time multilingual communication — completely offline. Let me show you."

---

## [0:25–1:20] DEMO 1 — Audio + Translation (55s)

**Screen:** Phone showing FirstVoice → tap "New Encounter"

**Voiceover:**

> "Here's how it works. I start a new encounter."

**Action:** Tap Survivor mic → speak in Hindi (e.g., "मुझे पानी चाहिए, मेरा बच्चा बीमार है")

> "A survivor speaks Hindi. Watch — Gemma 4's native audio encoder transcribes the speech directly. No Google Speech API, no internet. The 300-million parameter audio encoder inside Gemma 4 E4B handles this natively."

**Screen shows:** Recording indicator → "Transcribing with Gemma 4..." → Hindi text appears → English translation below

> "Language detected: Hindi. Translated to English instantly."

**Action:** Tap speaker icon 🔊 on the translation

> "The responder can hear it spoken aloud. Now the responder replies..."

**Action:** Tap Responder mic → speak English (e.g., "How many people are with you? Is anyone injured?")

**Screen shows:** English transcribed → Hindi translation appears → tap speaker icon

> "Gemma 4 translates to Hindi and speaks it for the survivor. Full bidirectional conversation — zero internet."

**KEY POINT (emphasize):**

> "What's special here: this is NOT a separate speech-to-text model plus a translation model. It's ONE model — Gemma 4 E4B — doing audio understanding, language detection, and translation. That's the power of Gemma 4's native multimodal architecture."

---

## [1:20–1:50] DEMO 2 — Vision + Triage Card (30s)

**Action:** Tap camera → photograph a disaster scene (or pre-loaded image)

> "Responders can also photograph the scene. Gemma 4's vision encoder analyzes structural damage and hazards."

**Screen shows:** "Analyzing with AI..." → assessment appears

**Action:** Tap "End Encounter" → "Generating triage card..."

> "When the encounter ends, Gemma 4 uses native function calling — the tools API — to generate a structured triage card. Urgency level, people count, needs categories, GPS. Seven seconds. No manual data entry."

**Screen shows:** Triage card with HIGH urgency, Medical + Extraction needs

---

## [1:50–2:20] DEMO 3 — Mesh Sync (30s)

**Screen:** Split view or picture-in-picture — TWO phones side by side

> "Now the real differentiator. In a disaster zone, there's no server. But responders need shared situational awareness."

**Action:** Show Phone 1 with triage cards → Show Phone 2 discovering peer via BLE → cards syncing

> "FirstVoice uses Bluetooth Low Energy mesh networking. Devices discover each other automatically — no pairing, no internet. Triage cards sync in seconds. An officer walking between disconnected zones becomes a data relay."

**Screen shows:** Same triage card appearing on second device

---

## [2:20–2:40] DEMO 4 — Dashboard + Map (20s)

**Action:** Open Dashboard screen

> "All triage cards — local and synced — appear on an offline map. Color-coded by urgency. Red is critical, orange is high. Team leads see the full picture at a glance."

**Screen shows:** Map with colored pins, timeline view

---

## [2:40–3:00] CLOSING — Future + Impact (20s)

**Screen:** Back to you or text overlay

> "What can be improved: inference speed will get faster as Gemma models optimize for mobile. The app can also work with cloud-hosted Ollama when internet IS available — giving deployment flexibility from field to command center.

> One phone. One model. Nine point six gigabytes. Apache 2.0. No servers, no subscriptions. Any government, any NGO, any military can deploy this freely.

> FirstVoice — when the tower falls, language shouldn't be a death sentence."

**Final frame:** "FirstVoice | Gemma 4 E4B | github.com/[YOUR_REPO]"

---

## RECORDING TIPS

1. **Speed up wait times** — Edit out the 10-18s transcription waits. Speed them up to 3-4s in the video. Judges don't want to watch loading.
2. **Use scrcpy** for clean screen recording: `scrcpy --record firstvoice-demo.mp4`
3. **Pre-warm the model** — Run one inference before recording so Gemma 4 is loaded in memory.
4. **Record voiceover separately** — Easier to get clean audio. Overlay on screen recording in editing.
5. **Two-phone sync** — Record second phone separately, edit as split-screen or picture-in-picture.
6. **Keep it under 3 minutes** — Judges watch many videos. Respect their time.

---

## WHAT'S COVERED

- ✅ Audio transcription (Gemma 4 native audio encoder)
- ✅ Why this approach is special (one model, multimodal, offline)
- ✅ Translation + TTS playback
- ✅ Vision analysis
- ✅ Triage card via function calling
- ✅ Mesh sync between devices (two phones)
- ✅ Dashboard/map
- ✅ Future improvements (speed, online availability)
- ✅ Impact statement
