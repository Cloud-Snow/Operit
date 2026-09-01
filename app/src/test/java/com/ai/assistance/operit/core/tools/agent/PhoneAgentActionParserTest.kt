package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 PhoneAgentActionParser 的确定性语义, 防止 UI 自动化子代理
 * 在模型输出 finish(...) 时被误解析成 do(...) 而陷入"一直 finish 不结束"的死循环。
 */
class PhoneAgentActionParserTest {

    private fun parse(raw: String): ParsedAgentAction {
        val (_, answer) = PhoneAgentActionParser.parseThinkingAndAction(raw)
        return PhoneAgentActionParser.parseAgentAction(answer ?: "")
    }

    @Test
    fun standardFinish_ends() {
        val action = parse("<think>任务已完成。</think>\n<answer>finish(message=\"全部完成\")</answer>")
        assertEquals("finish", action.metadata)
        assertEquals("全部完成", action.fields["message"])
    }

    @Test
    fun finishWithSpaces_ends() {
        val action = parse("<think>任务已完成。</think>\n<answer>finish (message = \"全部完成\")</answer>")
        assertEquals("finish", action.metadata)
        assertEquals("全部完成", action.fields["message"])
    }

    /** 死循环元凶之一: finish 的 message 里提到 do( */
    @Test
    fun finishMessageContainingDo_endsByFinish() {
        val action = parse("<think>ok</think>\n<answer>finish(message=\"done，无需再 do(action=Tap)\")</answer>")
        assertEquals("finish", action.metadata)
        assertTrue(action.fields["message"].orEmpty().contains("无需再"))
    }

    /** 死循环元凶之一: finish 之后还跟了一个多余的 do(...) */
    @Test
    fun trailingDoAfterFinish_endsByFinish() {
        val action = parse("<think>ok</think>\n<answer>finish(message=\"done\")\ndo(action=\"Note\", message=\"True\")</answer>")
        assertEquals("finish", action.metadata)
    }

    /** do(...) 在 finish(...) 之前: 先执行该 do, 执行完本轮即结束, 不丢最后一步动作。 */
    @Test
    fun doBeforeFinish_executesThenFinishes() {
        val action = parse("<think>ok</think>\n<answer>do(action=\"Tap\", element=[1,2])\nfinish(message=\"done\")</answer>")
        assertEquals("do", action.metadata)
        assertEquals("Tap", action.actionName)
        assertTrue(action.finishAfterDo)
        assertEquals("done", action.finishAfterDoMessage)
    }

    /** 正常动作轮: think 里提到 finish( 但 answer 只有 do(...) -> 继续循环, 不误杀。 */
    @Test
    fun normalDoRound_continues() {
        val action = parse("<think>做完这步就该 finish(message=\"...\") 了</think>\n<answer>do(action=\"Back\")</answer>")
        assertEquals("do", action.metadata)
        assertEquals("Back", action.actionName)
        assertFalse(action.finishAfterDo)
    }

    @Test
    fun bareFinishWithoutTags_ends() {
        val action = parse("finish(message=\"全部完成\")")
        assertEquals("finish", action.metadata)
    }

    @Test
    fun capitalFinish_ends() {
        val action = parse("<think>ok</think>\n<answer>Finish(message=\"done\")</answer>")
        assertEquals("finish", action.metadata)
    }

    @Test
    fun fullWidthParens_ends() {
        val action = parse("<think>ok</think>\n<answer>finish（message=\"done\"）</answer>")
        assertEquals("finish", action.metadata)
        assertEquals("done", action.fields["message"])
    }

    @Test
    fun singleQuoteMessage_extracts() {
        val action = parse("<think>ok</think>\n<answer>finish(message='done')</answer>")
        assertEquals("finish", action.metadata)
        assertEquals("done", action.fields["message"])
    }

    /** finish 只出现在 think 里且 answer 无命令时, 回退全文仍应判定 finish。 */
    @Test
    fun finishOnlyInThink_withNoAnswerCommand_fallsBack() {
        val action = parse("<think>任务完成，输出 finish(message=\"done\")</think>")
        assertEquals("finish", action.metadata)
    }

    @Test
    fun noCommandAtAll_unknown() {
        val action = parse("<think>ok</think>\n<answer>任务已经完成，无需操作</answer>")
        assertEquals("unknown", action.metadata)
    }
}
