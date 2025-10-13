package cn.solarmoon.spark_core.gas

import net.minecraft.network.codec.ByteBufCodecs

/**
 * ### 技能句柄（唯一标识符）
 * *使用内联，减少创建开销*
 */
@JvmInline
value class AbilityHandle(val id: Int) {
    fun isValid() = id > 0
    override fun toString() = "AbilityHandle($id)"

    companion object {
        val STREAM_CODEC = ByteBufCodecs.INT.map({ AbilityHandle(it) }, { it.id })
    }
}
