package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import com.mojang.serialization.Codec

object SparkAttachments {
    //
//    val ABILITY_SYSTEM_COMPONENT = SparkCore.REGISTER.attachment {
//        id = "ability_system_component"
//        factory = { holder ->  }
//    }

    /**
     * 区块实体方块高程索引的持久化 Attachment。
     * key = ChunkPos.toLong()（packed x/z），value = 升序排列的 Y 区间 ShortArray。
     *
     * 随 ServerLevel 持久化到存档，在 LevelEvent.Save 时写入，PhysicsLevelInitEvent 时加载。
     * 数据永不主动清理（空间换时间）。
     */
    val CHUNK_SOLID_INTERVALS = SparkCore.REGISTER.attachment<MutableMap<Long, ShortArray>> {
        id = "chunk_solid_intervals"
        factory = { mutableMapOf<Long, ShortArray>() }
        // 使用自定义 Codec（委托给 MapCodec 包装为 field-of）
        serializer = Codec.unboundedMap<Long, ShortArray>(Codec.LONG, Codec.SHORT.listOf().xmap(
            { list -> ShortArray(list.size) { i -> list[i] } },
            { arr -> arr.toList() }
        )).xmap(
            { it.toMutableMap() },
            { it.toMap() }
        )
        // 仅在非空时才序列化
        shouldSerialize = { it.isNotEmpty() }
    }

    @JvmStatic
    fun register() {}
}
