package cn.solarmoon.spark_core.physics.component

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.level.Level

enum class Authority {
    SERVER {
        override fun isInRightSide(level: Level): Boolean {
            return !level.isClientSide
        }
    },
    CLIENT {
        override fun isInRightSide(level: Level): Boolean {
            return level.isClientSide
        }
    };

    abstract fun isInRightSide(level: Level): Boolean

    companion object {
        @JvmStatic
        val STREAM_CODEC = ByteBufCodecs.INT.map(
            { Authority.entries[it] },
            { it.ordinal }
        )
    }

}