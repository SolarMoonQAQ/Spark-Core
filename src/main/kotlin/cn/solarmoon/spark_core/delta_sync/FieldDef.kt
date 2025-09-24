package cn.solarmoon.spark_core.delta_sync

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * 通用字段：
 * - maskBit: 位掩码位置
 * - codec: 序列化/反序列化工具
 * - apply: 将解码值应用到目标对象
 * - extract: 从对象提取当前值
 * - hasChanged: 判断字段是否变化（默认用 !=）
 */
data class FieldDef<Field, Data: Any>(
    val maskBit: Long,
    val codec: StreamCodec<ByteBuf, Field>,
    val apply: (target: Data, value: Field) -> Unit,
    val extract: (source: Data) -> Field,
    val hasChanged: (old: Field, new: Field) -> Boolean = { o, n -> o != n }
)
