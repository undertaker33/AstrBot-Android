package com.astrbot.android.data

internal data class OnDeviceTtsVoice(
    val id: String,
    val label: String,
    val speakerId: Int? = null,
    val type: String = "built_in",
)

internal data class OnDeviceTtsModel(
    val id: String,
    val label: String,
    val voices: List<OnDeviceTtsVoice>,
)

internal object OnDeviceTtsCatalog {
    private val kokoroVoices = listOf(
        OnDeviceTtsVoice("zf_001", "清甜少女", speakerId = 3),
        OnDeviceTtsVoice("zf_005", "软萌少女", speakerId = 7),
        OnDeviceTtsVoice("zf_018", "灵动少女", speakerId = 12),
        OnDeviceTtsVoice("zf_026", "冷淡御姐", speakerId = 18),
        OnDeviceTtsVoice("zf_036", "知性姐姐", speakerId = 22),
        OnDeviceTtsVoice("zf_044", "温婉姐姐", speakerId = 28),
        OnDeviceTtsVoice("zf_051", "轻柔学姐", speakerId = 33),
        OnDeviceTtsVoice("zf_060", "元气甜妹", speakerId = 35),
        OnDeviceTtsVoice("zf_071", "慵懒姐姐", speakerId = 38),
        OnDeviceTtsVoice("zf_079", "清冷御姐", speakerId = 46),
        OnDeviceTtsVoice("zm_009", "清朗少年", speakerId = 58),
        OnDeviceTtsVoice("zm_016", "温柔学长", speakerId = 65),
        OnDeviceTtsVoice("zm_025", "克制青年", speakerId = 67),
        OnDeviceTtsVoice("zm_041", "低沉男声", speakerId = 75),
        OnDeviceTtsVoice("zm_054", "稳重叔音", speakerId = 80),
        OnDeviceTtsVoice("zm_061", "疏离青年", speakerId = 85),
        OnDeviceTtsVoice("zm_068", "磁性男声", speakerId = 91),
        OnDeviceTtsVoice("zm_080", "冷峻男声", speakerId = 93),
        OnDeviceTtsVoice("zm_091", "成熟叔音", speakerId = 97),
        OnDeviceTtsVoice("zm_100", "厚实叔音", speakerId = 102),
    )

    val models = listOf(
        OnDeviceTtsModel(
            id = "kokoro",
            label = "kokoro（高性能设备）",
            voices = kokoroVoices,
        ),
    )

    fun model(modelId: String): OnDeviceTtsModel? = models.firstOrNull { it.id == modelId.trim().lowercase() }

    fun voicesFor(modelId: String): List<OnDeviceTtsVoice> = model(modelId)?.voices.orEmpty()

    fun defaultVoice(modelId: String): OnDeviceTtsVoice? = voicesFor(modelId).firstOrNull()

    fun voice(modelId: String, voiceId: String): OnDeviceTtsVoice? {
        val normalizedVoiceId = voiceId.trim()
        return voicesFor(modelId).firstOrNull { it.id == normalizedVoiceId }
    }
}
