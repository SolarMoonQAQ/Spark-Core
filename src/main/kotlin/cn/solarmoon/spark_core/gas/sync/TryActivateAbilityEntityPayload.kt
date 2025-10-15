package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.ActivationContext
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class TryActivateAbilityEntityPayload(
    val entityId: Int,
    val handle: AbilityHandle,
    val context: ActivationContext
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: TryActivateAbilityEntityPayload, context: IPayloadContext) {
            context.enqueueWork {
                val entity = context.player().level().getEntity(payload.entityId) ?: return@enqueueWork
                entity.abilitySystemComponent.findSpecFromHandle(payload.handle)?.tryActivate(payload.context)
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TryActivateAbilityEntityPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_activate_entity"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, TryActivateAbilityEntityPayload::entityId,
            AbilityHandle.STREAM_CODEC, TryActivateAbilityEntityPayload::handle,
            ActivationContext.STREAM_CODEC, TryActivateAbilityEntityPayload::context,
            ::TryActivateAbilityEntityPayload
        )
    }

}