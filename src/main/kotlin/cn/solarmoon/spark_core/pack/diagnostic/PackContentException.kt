package cn.solarmoon.spark_core.pack.diagnostic

import net.minecraft.network.chat.Component

/**
 * 内容错误：数据写错，而不是代码 bug。
 *
 * [writableStackTrace] 为 false，日志里只保留一行消息；消息使用 [Component] 以保留翻译能力。
 */
class PackContentException @JvmOverloads constructor(
    val userMessage: Component,
    cause: Throwable? = null,
) : RuntimeException(userMessage.string, cause, false, false)
