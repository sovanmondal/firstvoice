package com.firstvoice.app.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstvoice.app.data.model.PhraseCategory
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPhrasesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<PhraseCategory?>(null) }
    var selectedLanguage by remember { mutableStateOf("es") } // Default: Spanish
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Initialize TTS
    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = tts // TTS ready
            }
        }
        tts = textToSpeech
        onDispose { textToSpeech.shutdown() }
    }

    val phrases = remember { getQuickPhrases() }
    val filteredPhrases = remember(selectedCategory) {
        if (selectedCategory == null) phrases
        else phrases.filter { it.category == selectedCategory }
    }

    val languages = listOf(
        "es" to "Spanish", "fr" to "French", "ar" to "Arabic",
        "zh" to "Chinese", "hi" to "Hindi", "tr" to "Turkish",
        "ja" to "Japanese", "pt" to "Portuguese", "ru" to "Russian",
        "de" to "German"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Phrases") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Language selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target:", fontWeight = FontWeight.Medium)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = languages.find { it.first == selectedLanguage }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .width(160.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedLanguage = code
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Category filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
                PhraseCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                        },
                        label = { Text(cat.displayName()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phrase list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPhrases) { phrase ->
                    val translated = phrase.translations[selectedLanguage] ?: phrase.sourceText

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            // Speak the translated phrase
                            tts?.let { engine ->
                                val locale = Locale.forLanguageTag(selectedLanguage)
                                engine.language = locale
                                engine.speak(translated, TextToSpeech.QUEUE_FLUSH, null, phrase.id)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    phrase.sourceText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    translated,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = {
                                tts?.let { engine ->
                                    val locale = Locale.forLanguageTag(selectedLanguage)
                                    engine.language = locale
                                    engine.speak(translated, TextToSpeech.QUEUE_FLUSH, null, phrase.id)
                                }
                            }) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Speak",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick phrases data — in production loaded from data/quick-phrases.json.
 * Embedded here for immediate functionality.
 */
private data class QuickPhraseData(
    val id: String,
    val category: PhraseCategory,
    val sourceText: String,
    val translations: Map<String, String>
)

private fun getQuickPhrases(): List<QuickPhraseData> = listOf(
    QuickPhraseData("med-01", PhraseCategory.medical, "Are you injured?",
        mapOf("es" to "¿Estás herido?", "fr" to "Êtes-vous blessé ?", "ar" to "هل أنت مصاب؟", "zh" to "你受伤了吗？", "hi" to "क्या आप घायल हैं?", "tr" to "Yaralı mısınız?", "ja" to "怪我をしていますか？", "pt" to "Você está ferido?", "ru" to "Вы ранены?", "de" to "Sind Sie verletzt?")),
    QuickPhraseData("med-02", PhraseCategory.medical, "Where does it hurt?",
        mapOf("es" to "¿Dónde le duele?", "fr" to "Où avez-vous mal ?", "ar" to "أين يؤلمك؟", "zh" to "哪里疼？", "hi" to "कहाँ दर्द हो रहा है?", "tr" to "Nereniz ağrıyor?", "ja" to "どこが痛いですか？", "pt" to "Onde dói?", "ru" to "Где болит?", "de" to "Wo tut es weh?")),
    QuickPhraseData("med-03", PhraseCategory.medical, "Can you breathe?",
        mapOf("es" to "¿Puede respirar?", "fr" to "Pouvez-vous respirer ?", "ar" to "هل تستطيع التنفس؟", "zh" to "你能呼吸吗？", "hi" to "क्या आप सांस ले सकते हैं?", "tr" to "Nefes alabilir misiniz?", "ja" to "息ができますか？", "pt" to "Você consegue respirar?", "ru" to "Вы можете дышать?", "de" to "Können Sie atmen?")),
    QuickPhraseData("med-04", PhraseCategory.medical, "Do not move.",
        mapOf("es" to "No se mueva.", "fr" to "Ne bougez pas.", "ar" to "لا تتحرك.", "zh" to "不要动。", "hi" to "हिलिए मत।", "tr" to "Hareket etmeyin.", "ja" to "動かないでください。", "pt" to "Não se mova.", "ru" to "Не двигайтесь.", "de" to "Bewegen Sie sich nicht.")),
    QuickPhraseData("med-05", PhraseCategory.medical, "Medical help is on the way.",
        mapOf("es" to "La ayuda médica está en camino.", "fr" to "L'aide médicale est en route.", "ar" to "المساعدة الطبية في الطريق.", "zh" to "医疗救助正在赶来。", "hi" to "चिकित्सा सहायता आ रही है।", "tr" to "Tıbbi yardım yolda.", "ja" to "医療支援が向かっています。", "pt" to "A ajuda médica está a caminho.", "ru" to "Медицинская помощь уже в пути.", "de" to "Medizinische Hilfe ist unterwegs.")),
    QuickPhraseData("med-06", PhraseCategory.medical, "Can you walk?",
        mapOf("es" to "¿Puede caminar?", "fr" to "Pouvez-vous marcher ?", "ar" to "هل تستطيع المشي؟", "zh" to "你能走路吗？", "hi" to "क्या आप चल सकते हैं?", "tr" to "Yürüyebilir misiniz?", "ja" to "歩けますか？", "pt" to "Você consegue andar?", "ru" to "Вы можете ходить?", "de" to "Können Sie gehen?")),
    QuickPhraseData("safety-01", PhraseCategory.safety, "Help is coming.",
        mapOf("es" to "La ayuda viene en camino.", "fr" to "L'aide arrive.", "ar" to "المساعدة قادمة.", "zh" to "救援正在赶来。", "hi" to "मदद आ रही है।", "tr" to "Yardım geliyor.", "ja" to "助けが来ます。", "pt" to "A ajuda está vindo.", "ru" to "Помощь идёт.", "de" to "Hilfe kommt.")),
    QuickPhraseData("safety-02", PhraseCategory.safety, "Stay calm.",
        mapOf("es" to "Mantenga la calma.", "fr" to "Restez calme.", "ar" to "ابقَ هادئاً.", "zh" to "请保持冷静。", "hi" to "शांत रहें।", "tr" to "Sakin olun.", "ja" to "落ち着いてください。", "pt" to "Fique calmo.", "ru" to "Сохраняйте спокойствие.", "de" to "Bleiben Sie ruhig.")),
    QuickPhraseData("safety-03", PhraseCategory.safety, "This area is dangerous. Please move.",
        mapOf("es" to "Esta zona es peligrosa. Por favor, muévase.", "fr" to "Cette zone est dangereuse. Veuillez vous déplacer.", "ar" to "هذه المنطقة خطرة. يرجى التحرك.", "zh" to "这个区域很危险。请转移。", "hi" to "यह क्षेत्र खतरनाक है। कृपया हटें।", "tr" to "Bu alan tehlikeli. Lütfen uzaklaşın.", "ja" to "この場所は危険です。移動してください。")),
    QuickPhraseData("safety-04", PhraseCategory.safety, "You are safe now.",
        mapOf("es" to "Ahora está a salvo.", "fr" to "Vous êtes en sécurité maintenant.", "ar" to "أنت بأمان الآن.", "zh" to "你现在安全了。", "hi" to "अब आप सुरक्षित हैं।", "tr" to "Artık güvendesiniz.", "ja" to "もう安全です。")),
    QuickPhraseData("safety-05", PhraseCategory.safety, "I am a rescue worker. I am here to help you.",
        mapOf("es" to "Soy un trabajador de rescate. Estoy aquí para ayudarle.", "fr" to "Je suis un secouriste. Je suis ici pour vous aider.", "ar" to "أنا عامل إنقاذ. أنا هنا لمساعدتك.", "zh" to "我是救援人员。我来帮助你。", "hi" to "मैं बचाव कर्मी हूँ। मैं आपकी मदद के लिए यहाँ हूँ।", "tr" to "Ben bir kurtarma görevlisiyim. Size yardım etmek için buradayım.")),
    QuickPhraseData("logistics-01", PhraseCategory.logistics, "How many people are with you?",
        mapOf("es" to "¿Cuántas personas están con usted?", "fr" to "Combien de personnes sont avec vous ?", "ar" to "كم شخصاً معك؟", "zh" to "有多少人和你在一起？", "hi" to "आपके साथ कितने लोग हैं?", "tr" to "Yanınızda kaç kişi var?", "ja" to "何人一緒にいますか？")),
    QuickPhraseData("logistics-02", PhraseCategory.logistics, "Is anyone trapped?",
        mapOf("es" to "¿Hay alguien atrapado?", "fr" to "Quelqu'un est-il coincé ?", "ar" to "هل هناك أحد محاصر؟", "zh" to "有人被困吗？", "hi" to "क्या कोई फंसा हुआ है?", "tr" to "Mahsur kalan var mı?", "ja" to "閉じ込められている人はいますか？")),
    QuickPhraseData("logistics-03", PhraseCategory.logistics, "Do you need water?",
        mapOf("es" to "¿Necesita agua?", "fr" to "Avez-vous besoin d'eau ?", "ar" to "هل تحتاج ماء؟", "zh" to "你需要水吗？", "hi" to "क्या आपको पानी चाहिए?", "tr" to "Suya ihtiyacınız var mı?", "ja" to "水が必要ですか？")),
    QuickPhraseData("logistics-04", PhraseCategory.logistics, "Do you need shelter?",
        mapOf("es" to "¿Necesita refugio?", "fr" to "Avez-vous besoin d'un abri ?", "ar" to "هل تحتاج مأوى؟", "zh" to "你需要避难所吗？", "hi" to "क्या आपको आश्रय चाहिए?", "tr" to "Barınağa ihtiyacınız var mı?", "ja" to "避難所が必要ですか？")),
    QuickPhraseData("id-01", PhraseCategory.identification, "What is your name?",
        mapOf("es" to "¿Cómo se llama?", "fr" to "Comment vous appelez-vous ?", "ar" to "ما اسمك؟", "zh" to "你叫什么名字？", "hi" to "आपका नाम क्या है?", "tr" to "Adınız nedir?", "ja" to "お名前は何ですか？")),
    QuickPhraseData("id-02", PhraseCategory.identification, "Do you have family nearby?",
        mapOf("es" to "¿Tiene familia cerca?", "fr" to "Avez-vous de la famille à proximité ?", "ar" to "هل لديك عائلة قريبة؟", "zh" to "你附近有家人吗？", "hi" to "क्या आपके परिवार के लोग पास में हैं?", "tr" to "Yakınlarınız buralarda mı?", "ja" to "近くに家族はいますか？")),
    QuickPhraseData("id-03", PhraseCategory.identification, "Are there children with you?",
        mapOf("es" to "¿Hay niños con usted?", "fr" to "Y a-t-il des enfants avec vous ?", "ar" to "هل يوجد أطفال معك؟", "zh" to "有孩子和你在一起吗？", "hi" to "क्या आपके साथ बच्चे हैं?", "tr" to "Yanınızda çocuk var mı?", "ja" to "お子さんは一緒ですか？"))
)
