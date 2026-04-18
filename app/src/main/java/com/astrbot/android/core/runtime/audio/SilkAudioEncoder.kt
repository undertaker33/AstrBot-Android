package com.astrbot.android.core.runtime.audio

import android.content.Context
import java.io.File

object SilkAudioEncoder {
    fun initialize(context: Context) {
        TencentSilkEncoder.initialize(context)
    }

    fun encode(inputFile: File): File {
        return TencentSilkEncoder.encode(inputFile)
    }
}
