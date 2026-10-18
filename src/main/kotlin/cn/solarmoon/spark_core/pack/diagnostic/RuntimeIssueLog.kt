package cn.solarmoon.spark_core.pack.diagnostic

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.network.chat.Component
import org.slf4j.Logger

/**
 * 不属于任何内容包帧的运行时问题出口（例如事件订阅者抛异常）。
 */
object RuntimeIssueLog {

    internal val LOGGER: Logger = SparkCore.logger("内容包诊断")

    @JvmStatic
    @JvmOverloads
    fun warn(message: Component, cause: Throwable? = null) {
        if (cause == null) LOGGER.warn(message.string) else LOGGER.warn(message.string, cause)
    }

    @JvmStatic
    @JvmOverloads
    fun error(message: Component, cause: Throwable? = null) {
        if (cause == null) LOGGER.error(message.string) else LOGGER.error(message.string, cause)
    }
}
