package cn.solarmoon.spark_core.util

import net.minecraft.network.codec.ByteBufCodecs

enum class Side {
    LEFT, RIGHT, FRONT, BACK;

    override fun toString(): String {
        return super.toString().lowercase()
    }

    companion object {
        @JvmStatic
        val STREAM_CODEC = ByteBufCodecs.INT.map(
            { Side.entries[it] },
            { it.ordinal }
        )
    }
}