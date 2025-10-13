package cn.solarmoon.spark_core.gas

import com.mojang.serialization.Codec
import net.minecraft.network.codec.ByteBufCodecs

data class GameplayTag(
    val path: String
) {
    val parts: List<String> = path.split(".")

    /** 判断是否是另一个标签的子标签 */
    fun matches(other: GameplayTag): Boolean {
        if (other.parts.size > parts.size) return false
        return other.parts == parts.subList(0, other.parts.size)
    }

    override fun toString() = path

    companion object {
        val STEAM_CODEC = ByteBufCodecs.STRING_UTF8.map({ GameplayTag(it) }, { it.path })

        val CODEC = Codec.STRING.xmap({ GameplayTag(it) }, { it.path })
    }
}