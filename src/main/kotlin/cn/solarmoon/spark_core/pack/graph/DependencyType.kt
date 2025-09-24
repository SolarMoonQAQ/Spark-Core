package cn.solarmoon.spark_core.pack.graph

import com.mojang.serialization.Codec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.util.ByIdMap
import net.minecraft.util.StringRepresentable

enum class DependencyType: StringRepresentable {
    HARD, SOFT, OVERRIDE, CONFLICT;

    override fun getSerializedName(): String = toString().lowercase()

    companion object {
        val CODEC: Codec<DependencyType> = StringRepresentable.fromEnum(::values)

        val STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(
                DependencyType::ordinal,
                DependencyType.entries.toTypedArray(),
                ByIdMap.OutOfBoundsStrategy.ZERO
            ),
            DependencyType::ordinal
        )
    }

}