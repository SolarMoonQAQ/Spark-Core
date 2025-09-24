package cn.solarmoon.spark_core.delta_sync

import cn.solarmoon.spark_core.data.readNullable
import cn.solarmoon.spark_core.data.writeNullable
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs

data class DiffPacket(
    val schemaId: Int,
    val mask: Long,
    val changes: Map<Long, Any?> // key 是 maskBit，value 是变化值
)  {

    fun ifValid(consumer: DiffPacket.() -> Unit) {
        if (this.mask != 0L) {
            consumer.invoke(this)
        }
    }

    companion object {
        val STREAM_CODEC = NeoForgeStreamCodecs.lazy {
            object : StreamCodec<ByteBuf, DiffPacket> {
                override fun encode(buf: ByteBuf, packet: DiffPacket) {
                    val schema = SparkRegistries.DIFF_SYNC_SCHEMA.byId(packet.schemaId)!!
                    buf.writeInt(packet.schemaId)
                    buf.writeLong(packet.mask)
                    for (field in schema.fields) {
                        if ((packet.mask and field.maskBit) != 0L) {
                            val codec = field.codec as StreamCodec<ByteBuf, Any?>
                            val value = packet.changes[field.maskBit]
                            buf.writeNullable(value, codec)
                        }
                    }
                }

                override fun decode(buf: ByteBuf): DiffPacket {
                    val schemaId = buf.readInt()
                    val schema = SparkRegistries.DIFF_SYNC_SCHEMA.byId(schemaId)!!
                    val mask = buf.readLong()
                    val changes = mutableMapOf<Long, Any?>()
                    for (field in schema.fields) {
                        if ((mask and field.maskBit) != 0L) {
                            val codec = field.codec
                            val value = buf.readNullable(codec)
                            changes[field.maskBit] = value
                        }
                    }
                    return DiffPacket(schemaId, mask, changes)
                }
            }
        }

    }
}
