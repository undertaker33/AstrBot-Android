package com.astrbot.android.runtime.botcommand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BotCommandParserTest {

    @Test
    fun `parser recognizes help sid agent and ls commands`() {
        assertEquals("help", BotCommandParser.parse("/help")?.name)
        assertEquals("sid", BotCommandParser.parse("/sid")?.name)
        assertEquals("agent", BotCommandParser.parse("/agent")?.name)
        assertEquals("ls", BotCommandParser.parse("/ls 2")?.name)
        assertEquals("switch", BotCommandParser.parse("/switch 2")?.name)
        assertEquals("new", BotCommandParser.parse("/new")?.name)
        assertEquals("groupnew", BotCommandParser.parse("/groupnew")?.name)
        assertEquals("del", BotCommandParser.parse("/del")?.name)
        assertEquals("rename", BotCommandParser.parse("/rename 新会话")?.name)
    }

    @Test
    fun `parser preserves persona arguments and ignores plain text`() {
        val command = BotCommandParser.parse("/persona view Default Assistant")
        assertEquals("persona", command?.name)
        assertEquals(listOf("view", "Default", "Assistant"), command?.arguments)
        assertNull(BotCommandParser.parse("hello world"))
    }
}
