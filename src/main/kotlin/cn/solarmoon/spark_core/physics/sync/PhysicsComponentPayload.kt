package cn.solarmoon.spark_core.physics.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.delta_sync.DiffPacket
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.physics.component.allCollisionComponents
import cn.solarmoon.spark_core.physics.component.getCollisionComponent
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class PhysicsComponentPayload(
    val id: Long,
    val delta: DiffPacket
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: PhysicsComponentPayload, context: IPayloadContext) {
            context.enqueueWork {
                context.player().level().allCollisionComponents.values.forEach {
                    (it.diffSyncSchema as DiffSyncSchema<Any>).applyDiff(payload.delta, it)
                }
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<PhysicsComponentPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "physics"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PhysicsComponentPayload::id,
            DiffPacket.STREAM_CODEC, PhysicsComponentPayload::delta,
            ::PhysicsComponentPayload
        )
    }

}