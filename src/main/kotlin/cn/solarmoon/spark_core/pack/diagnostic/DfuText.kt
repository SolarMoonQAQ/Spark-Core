package cn.solarmoon.spark_core.pack.diagnostic

import net.minecraft.network.chat.Component

/**
 * 把 DFU 的错误消息转换成可读的 [Component]。
 *
 * DFU 在缺字段时会把整个输入对象拼进消息（`No key mass in {……}`），这里只保留字段名；
 * 其他消息截断到 300 字符，避免整份文件进聊天框。
 */
object DfuText {

    private const val MAX_LENGTH = 300
    private val NO_KEY = Regex("""No key (\S+) in """)

    @JvmStatic
    fun toComponent(raw: String): Component {
        NO_KEY.find(raw)?.let {
            return Component.translatableWithFallback(
                "spark_core.pack.error.missing_field",
                "Missing required field: %s",
                it.groupValues[1],
            )
        }
        return Component.literal(raw.take(MAX_LENGTH))
    }
}
