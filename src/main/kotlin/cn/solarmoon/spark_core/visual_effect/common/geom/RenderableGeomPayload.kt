package cn.solarmoon.spark_core.visual_effect.common.geom

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.awt.Color

data class RenderableGeomPayload(
    val id: String,
    val color: Int?,
    val boxId: Int?
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: RenderableGeomPayload, context: IPayloadContext) {
            val debug = SparkVisualEffects.GEOM.getRenderableBox(payload.id)
            payload.color?.let { debug.setColor(Color(it)) }
//            payload.boxId?.let { debug.box = DxHelper.getGeom(it) }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<RenderableGeomPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "box"))

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, RenderableGeomPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): RenderableGeomPayload {
                val id = buffer.readUtf()
                val color = buffer.readNullable(ByteBufCodecs.INT)
                val box = buffer.readNullable(ByteBufCodecs.INT)
                return RenderableGeomPayload(id, color, box)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: RenderableGeomPayload) {
                buffer.writeUtf(value.id)
                buffer.writeNullable(value.color, ByteBufCodecs.INT)
                buffer.writeNullable(value.boxId, ByteBufCodecs.INT)
            }
        }
    }

}