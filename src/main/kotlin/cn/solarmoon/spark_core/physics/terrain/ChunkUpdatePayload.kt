package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.minecraft.core.SectionPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class TerrainUpdatePayload(val sections: Set<SectionPos>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handler(payload: TerrainUpdatePayload, context: IPayloadContext) {
            val level = context.player().level()
            val physicsLevel: PhysicsLevel = level.physicsLevel
            val terrainManager: PhysicsChunkManager = physicsLevel.terrainManager
            context.enqueueWork {
                terrainManager.markDirtySections(payload.sections)
            }
        }

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TerrainUpdatePayload> = StreamCodec.of(
            { buf, payload ->
                // 编码：写入section数量
                buf.writeVarInt(payload.sections.size)

                // 编码：写入每个section的坐标
                payload.sections.forEach { sectionPos ->
                    buf.writeVarInt(sectionPos.x())
                    buf.writeVarInt(sectionPos.y())
                    buf.writeVarInt(sectionPos.z())
                }
            },
            { buf ->
                // 解码：读取section数量
                val sectionCount = buf.readVarInt()

                // 解码：读取每个section的坐标并创建SectionPos
                val sections = mutableSetOf<SectionPos>()
                repeat(sectionCount) {
                    val x = buf.readVarInt()
                    val y = buf.readVarInt()
                    val z = buf.readVarInt()
                    sections.add(SectionPos.of(x, y, z))
                }

                TerrainUpdatePayload(sections)
            }
        )

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TerrainUpdatePayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "terrain_update")
        )
    }

}