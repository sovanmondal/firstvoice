# FirstVoice — YouTube Upload Metadata

Copy/paste these into the YouTube upload form.

---

## TITLE (max 100 chars)

**Primary (95 chars):**
```
FirstVoice: Offline Disaster Communication Agent on Gemma 4 | Gemma 4 Good Hackathon Submission
```

**Alternative shorter (78 chars):**
```
FirstVoice — AI-Powered Crisis Communication, Fully Offline | Gemma 4 Demo
```

**Alternative bolder (74 chars):**
```
When the Network Dies, FirstVoice Speaks | Gemma 4 Offline Crisis Agent
```

---

## DESCRIPTION (paste this exactly)

```
FirstVoice turns any Android phone into an AI-powered communication bridge between disaster responders and survivors who don't share a language. Built for the Gemma 4 Good Hackathon (Kaggle × Google DeepMind), under the Global Resilience track.

🌍 The Problem
On January 26, 2001, a 7.7 magnitude earthquake struck Gujarat, India. 20,000 people died. International rescue teams arrived within hours — but couldn't communicate with trapped survivors speaking Gujarati, Kutchi, and Sindhi. People died not because help didn't arrive, but because help couldn't talk.

This pattern repeats: Turkey-Syria 2023, Morocco 2023, Myanmar 2024. When infrastructure collapses, communication collapses with it.

🎙️ What FirstVoice Does (All Offline)
• Push-to-talk audio → Gemma 4 transcribes natively (no Android STT, no internet)
• Auto language detection + translation across 140+ languages
• TTS playback of every message in the target language
• Photo capture → Gemma 4 vision encoder analyzes damage / injuries / hazards
• AI-generated triage cards via native function calling (urgency, needs, GPS, summary)
• BLE mesh sync — triage cards auto-share between responder phones, no server, no router
• Voice walkie-talkie over the mesh
• Pre-cached offline map dashboard
• 30+ pre-translated emergency phrases for instant communication

🛠️ How We Use Gemma 4
Model: Gemma 4 E4B (4.5B effective params, 9.6GB via Ollama)
1. Native audio encoder (300M params) — transcription + language detection in one call
2. Native vision encoder — damage/injury/hazard severity assessment
3. Native function calling via Ollama tools API — structured triage cards, no regex
4. 140+ language support, 128K context, system role support

✅ Tested on Physical Devices
OPPO CPH2401 + Samsung Galaxy M15 5G connected to a Mac M4 running Ollama via USB ADB reverse port forwarding. Triage card generation in 7 seconds. BLE mesh sync confirmed between two devices. Zero network calls after model download.

📈 Impact
Deployment cost: $200 Android phone + Apache 2.0 model. No servers, no subscriptions.

Target scenarios: NDRF/SDRF disaster response across India's 22 official languages, UNHCR refugee registration, mass casualty triage, military CASEVAC in foreign deployments, pandemic field teams in rural areas.

🔗 Project Links
• Kaggle Submission: [paste your Kaggle URL]
• GitHub: [paste your GitHub URL]
• Gemma 4 Model: https://huggingface.co/google/gemma-4-E4B-it
• Ollama: https://ollama.com

Built for the Gemma 4 Good Hackathon (Kaggle × Google DeepMind, May 2026)

#Gemma4 #Kaggle #DisasterResponse #EdgeAI #OfflineAI #Multilingual #Gemma #AIforGood #OpenSource #GoogleDeepMind #Hackathon #Android #Crisis #HumanitarianTech
```

---

## TAGS (paste comma-separated)

```
Gemma 4, Gemma, Kaggle, Hackathon, AI, Disaster Response, Crisis Communication, Multilingual, Offline AI, Edge AI, On-Device AI, Function Calling, Vision AI, Audio AI, Translation, Bengali, Hindi, BLE Mesh, Bluetooth Mesh, Android, Kotlin, Jetpack Compose, Ollama, Open Source, Apache 2.0, Google DeepMind, NDRF, Refugee, UNHCR, Triage, Emergency Response, Humanitarian Technology, AI for Good
```

---

## SETTINGS (CRITICAL for Kaggle eligibility)

| Setting | Value |
|---------|-------|
| Visibility | **Public** ✅ |
| Made for kids | **No, it's not made for kids** |
| Audience | **Not made for kids** |
| Comments | On (or Off — your choice) |
| Age restriction | **No** |
| Embedding | **Allow embedding** ✅ |
| Category | **Science & Technology** |
| License | **Standard YouTube License** |
| Language | English |
| Recording date | (optional) May 2026 |
| Location | (optional) India |

---

## THUMBNAIL CONCEPT

If you want to make a custom thumbnail (1280×720):
- Left: split-screen image of two phones side-by-side (Responder 1 + Responder 2)
- Right: bold text "FirstVoice" + tagline "When the Network Dies"
- Bottom: small badges — "Gemma 4 • Offline • 140 Languages"
- Color palette: dark blue/red emergency tones (not the usual green agritech feel)

You can also use any frame from the side-by-side video (CapCut "Set as cover" feature).

---

## FINAL CHECKS BEFORE PUBLISHING

- [ ] Test the published URL in **incognito mode** — must play without login
- [ ] Confirm visibility is **Public** (not Unlisted)
- [ ] Embedding is **Allowed**
- [ ] Title and description filled
- [ ] Tags pasted
- [ ] Description includes Kaggle Submission URL after you submit
- [ ] Description includes GitHub URL once repo is public

---

## QUICK COPY COMMANDS

Copy title to clipboard:
```bash
echo "FirstVoice: Offline Disaster Communication Agent on Gemma 4 | Gemma 4 Good Hackathon Submission" | pbcopy
```

Copy full description block:
```bash
sed -n '/^## DESCRIPTION/,/^## TAGS/p' /Users/sovanm/Documents/Hackathons/Medlens/docs/youtube-upload.md | sed '1,3d;/^## TAGS/d;/^```$/d' | pbcopy
```

Copy tags:
```bash
echo "Gemma 4, Gemma, Kaggle, Hackathon, AI, Disaster Response, Crisis Communication, Multilingual, Offline AI, Edge AI, On-Device AI, Function Calling, Vision AI, Audio AI, Translation, Bengali, Hindi, BLE Mesh, Bluetooth Mesh, Android, Kotlin, Jetpack Compose, Ollama, Open Source, Apache 2.0, Google DeepMind, NDRF, Refugee, UNHCR, Triage, Emergency Response, Humanitarian Technology, AI for Good" | pbcopy
```
