package cn.solarmoon.spark_core.delta_sync

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs


data class DiffPacket(
    val schemaId: Short,
    val mask: Long,
    val nullMask: Long,
    val values: List<Any?> // 顺序与 schema.fields 对应
) {
    fun ifValid(consumer: DiffPacket.() -> Unit) {
        if (this.mask != 0L) consumer(this)
    }

    companion object {
        val STREAM_CODEC = NeoForgeStreamCodecs.lazy {
            object : StreamCodec<ByteBuf, DiffPacket> {
                override fun encode(buf: ByteBuf, packet: DiffPacket) {
                    val schema = SparkRegistries.DIFF_SYNC_SCHEMA.byId(packet.schemaId.toInt())!!
                    buf.writeShort(packet.schemaId.toInt())
                    buf.writeLong(packet.mask)
                    buf.writeLong(packet.nullMask)

                    var index = 0
                    for (field in schema.fields) {
                        if ((packet.mask and field.maskBit) != 0L) {
                            val isNull = (packet.nullMask and field.maskBit) != 0L
                            val value = packet.values[index++]
                            if (!isNull && value != null) {
                                val codec = field.codec as StreamCodec<ByteBuf, Any?>
                                codec.encode(buf, value)
                            }
                        }
                    }
                }

                override fun decode(buf: ByteBuf): DiffPacket {
                    val schemaId = buf.readShort()
                    val schema = SparkRegistries.DIFF_SYNC_SCHEMA.byId(schemaId.toInt())!!
                    val mask = buf.readLong()
                    val nullMask = buf.readLong()

                    val values = mutableListOf<Any?>()
                    for (field in schema.fields) {
                        if ((mask and field.maskBit) != 0L) {
                            val isNull = (nullMask and field.maskBit) != 0L
                            if (isNull) {
                                values.add(null)
                            } else {
                                val codec = field.codec
                                val value = codec.decode(buf)
                                values.add(value)
                            }
                        }
                    }
                    return DiffPacket(schemaId, mask, nullMask, values)
                }
            }
        }
    }
}
