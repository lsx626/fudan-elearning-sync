package edu.fudan.elearning.sync.office

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 文本清洗单测：PPT 的软换行、OOXML 字面转义、控制字符与代理对（emoji）。
 *
 * 这些字符是「文件里部分字符显示异常（方块/乱码）」的直接来源。
 */
class TextSanitizerTest {

    @Test
    fun softBreakAndFormFeedBecomeNewline() {
        assertEquals("第一行\n第二行", TextSanitizer.clean("第一行\u000B第二行"))
        assertEquals("A\nB", TextSanitizer.clean("A\u000CB"))
    }

    @Test
    fun ooxmlLiteralEscapesAreDecoded() {
        assertEquals("第一行\n第二行", TextSanitizer.clean("第一行_x000B_第二行"))
        // 回车标记由段落结构表达，直接丢弃
        assertEquals("文本", TextSanitizer.clean("文本_x000D_"))
        assertEquals("A\tB", TextSanitizer.clean("A_x0009_B"))
    }

    @Test
    fun controlCharactersAreDropped() {
        assertEquals("标题正文", TextSanitizer.clean("标题\u0001\u0007正文"))
        assertEquals("abc", TextSanitizer.clean("abc\u007F"))
        assertEquals("", TextSanitizer.clean("\u0000\u0001\u0002"))
    }

    @Test
    fun keepsNormalTextAndSurrogatePairs() {
        assertEquals("普通中文 with English 123", TextSanitizer.clean("普通中文 with English 123"))
        // emoji 是代理对：不能被按 char 拆坏
        assertEquals("进度 👍 完成", TextSanitizer.clean("进度 👍 完成"))
    }

    @Test
    fun normalizesLineEndings() {
        // CRLF 归一成单个 \n；孤立的 CR 也当作换行
        assertEquals("a\nb\nc", TextSanitizer.clean("a\r\nb\nc"))
        assertEquals("\n单行", TextSanitizer.clean("\r单行"))
    }

    @Test
    fun handlesNullAndEmpty() {
        assertEquals("", TextSanitizer.clean(null))
        assertEquals("", TextSanitizer.clean(""))
    }
}
