package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.ActivationContext
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class ActivateAbilityPayload(
    val entityId: Int,
    val handle: AbilityHandle,
    val context: ActivationContext
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: ActivateAbilityPayload, context: IPayloadContext) {
            context.enqueueWork {
                val entity = context.player().level().getEntity(payload.entityId) ?: return@enqueueWork
                entity.abilitySystemComponent.getSpec(payload.handle)?.tryActivate(payload.context)
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<ActivateAbilityPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_activate"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ActivateAbilityPayload::entityId,
            AbilityHandle.STREAM_CODEC, ActivateAbilityPayload::handle,
            ActivationContext.STREAM_CODEC, ActivateAbilityPayload::context,
            ::ActivateAbilityPayload
        )
    }

}