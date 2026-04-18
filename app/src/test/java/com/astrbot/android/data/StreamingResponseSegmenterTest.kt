package com.astrbot.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResponseSegmenterTest {
    @Test
    fun split_recognizes_chinese_sentence_boundaries() {
        val text = "第一句来了，这里稍微长一点。第二句继续补充，方便观察分段。第三句收尾。"

        val segments = LlmResponseSegmenter.split(text)

        assertTrue(segments.size >= 2)
        assertEquals(text, segments.joinToString(""))
    }

    @Test
    fun split_falls_back_to_multiple_segments_for_long_chinese_plain_text() {
        val text = "这是一个没有显式英文边界但足够长的中文回复我们希望它不要整段发出而是至少能被切成两段继续发送给QQ侧观察"

        val segments = LlmResponseSegmenter.split(text)

        assertTrue(segments.size >= 2)
        assertEquals(text, segments.joinToString(""))
    }
}
