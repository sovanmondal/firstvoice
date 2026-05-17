package com.firstvoice.app

import android.app.Application
import kotlinx.coroutines.*

class FirstVoiceApp : Application() {

    lateinit var container: com.firstvoice.app.di.AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = com.firstvoice.app.di.AppContainer(this)

        // Initialize OSMDroid configuration for offline maps
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = "FirstVoice/1.0"
            osmdroidBasePath = java.io.File(filesDir, "osmdroid")
            osmdroidTileCache = java.io.File(filesDir, "osmdroid/tiles")
        }

        // Start battery monitoring
        container.batteryMonitor.start()

        // Start mesh sync listener (always-on, ready to receive)
        try {
            android.util.Log.d("FirstVoiceApp", "About to init meshSyncService...")
            container.meshSyncService.initialize()
            android.util.Log.d("FirstVoiceApp", "meshSyncService initialized OK")
        } catch (e: Exception) {
            android.util.Log.e("FirstVoiceApp", "MeshSync init FAILED", e)
        }

        // Seed 10 earthquake triage cards in Kolkata
        CoroutineScope(Dispatchers.IO).launch {
            val dao = container.database.triageCardDao()
            if (dao.getById("eq-01") != null) return@launch
            val now = System.currentTimeMillis()
            val cards = listOf(
                t("eq-01", 22.5726, 88.3639, now, 8, "CRITICAL", "Medical,Extraction", "Bengali",
                    "Multi-story building collapsed near Park Street. 8 people trapped in rubble. Screams heard from 2nd floor. Immediate heavy rescue equipment needed."),
                t("eq-02", 22.5744, 88.3621, now-120000, 12, "CRITICAL", "Medical,Shelter,WaterFood", "Hindi",
                    "Residential block pancaked near Mullick Bazar. 12 residents unaccounted for. Gas leak detected. Evacuate 200m radius immediately."),
                t("eq-03", 22.5708, 88.3657, now-240000, 5, "HIGH", "Medical,FamilyReunification", "Bengali",
                    "School building partially collapsed near Taltala. 5 children with injuries. Teachers performing first aid. Parents gathering outside."),
                t("eq-04", 22.5690, 88.3620, now-360000, 3, "HIGH", "Medical,Extraction", "Bengali",
                    "Elderly couple and caretaker trapped in ground floor flat near Ripon Street. Structure unstable. Neighbors report hearing voices."),
                t("eq-05", 22.5762, 88.3640, now-480000, 20, "HIGH", "Shelter,WaterFood", "Hindi",
                    "200+ displaced people gathering at Maidan open ground. No shelter from sun. Children and elderly dehydrating. Need water tankers urgently."),
                t("eq-06", 22.5710, 88.3600, now-600000, 2, "CRITICAL", "Medical,Extraction", "Bengali",
                    "Hospital ward ceiling collapsed at SSKM. 2 patients buried under debris. Medical staff injured. ICU power backup failing."),
                t("eq-07", 22.5748, 88.3670, now-720000, 6, "MEDIUM", "Medical,Shelter", "Bengali",
                    "Family of 6 evacuated from cracked building near Sealdah. Minor cuts and bruises. Building tilting visibly. Need structural assessment."),
                t("eq-08", 22.5680, 88.3650, now-840000, 4, "MEDIUM", "Medical,FamilyReunification", "Hindi",
                    "4 migrant workers found disoriented near Entally market. Head injuries from falling debris. Cannot locate their co-workers. Speaking Hindi."),
                t("eq-09", 22.5730, 88.3680, now-960000, 1, "LOW", "Medical", "Bengali",
                    "Elderly woman with minor leg wound near Bow Barracks. Ambulatory. Requesting bandage and tetanus shot. Family contacted."),
                t("eq-10", 22.5700, 88.3590, now-1080000, 15, "HIGH", "Shelter,WaterFood,Medical", "Bengali",
                    "Slum area near Kidderpore heavily damaged. 15+ families homeless. Multiple minor injuries. Open fires from broken gas lines. Fire brigade needed.")
            )
            dao.insertAll(cards)
            android.util.Log.d("FirstVoiceApp", "Seeded 10 earthquake triage cards in Kolkata")
        }
    }

    private fun t(id: String, lat: Double, lon: Double, ts: Long, people: Int, urgency: String, needs: String, lang: String, summary: String): com.firstvoice.app.data.local.entity.TriageCardEntity {
        val u = com.firstvoice.app.data.model.UrgencyLevel.valueOf(urgency)
        val n = needs.split(",").map { com.firstvoice.app.data.model.NeedsCategory.valueOf(it) }
        return com.firstvoice.app.data.local.entity.TriageCardEntity(
            id = id, deviceId = "fv-kolkata", sessionId = "eq-session",
            latitude = lat, longitude = lon, gpsAccuracy = 10f, gpsTimestamp = ts,
            timestamp = ts, updatedAt = ts, peopleCount = people,
            urgencyLevel = u, needsCategories = n, detectedLanguage = lang,
            assessmentSummary = summary, sourceDataRefs = emptyList(), photos = emptyList(),
            syncStatus = com.firstvoice.app.data.model.SyncStatus()
        )
    }

    companion object {
        lateinit var instance: FirstVoiceApp
            private set
    }
}
