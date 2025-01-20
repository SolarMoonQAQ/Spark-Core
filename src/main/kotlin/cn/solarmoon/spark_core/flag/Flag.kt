package cn.solarmoon.spark_core.flag

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

open class Flag(
    val name: String,
) {

    companion object {
        @JvmStatic
        val CODEC: Codec<Flag> = Codec.STRING.xmap({ Flag(it) }, { it.name })

        @JvmStatic
        val MAP_CODEC = Codec.unboundedMap(CODEC, Codec.BOOL).xmap( { LinkedHashMap(it) }, { it } )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Flag) return false
        return other.name == name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}