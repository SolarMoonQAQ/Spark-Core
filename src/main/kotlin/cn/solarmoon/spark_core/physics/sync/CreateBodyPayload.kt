package cn.solarmoon.spark_core.physics.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.delta_sync.DiffPacket
import cn.solarmoon.spark_core.physics.component.Authority
import cn.solarmoon.spark_core.physics.component.CollisionObjectType
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class CreateBodyPayload(
    val id: Long,
    val name: String,
    val authority: Authority,
    val type: CollisionObjectType<*>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: CreateBodyPayload, context: IPayloadContext) {
            context.enqueueWork {
                val body = payload.type.create(payload.name, payload.authority, BoxCollisionShape(0f), context.player().level())
                body.addToLevel()
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<CreateBodyPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "physics"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PhysicsComponentPayload::id,
            DiffPacket.STREAM_CODEC, PhysicsComponentPayload::delta,
            ::PhysicsComponentPayload
        )
    }

}