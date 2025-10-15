package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.ActivationContext
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class TryActivateAbilityLocalPayload(
    val handle: AbilityHandle,
    val context: ActivationContext
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInServer(payload: TryActivateAbilityLocalPayload, context: IPayloadContext) {
            context.enqueueWork {
                val requester = context.player()
                requester.abilitySystemComponent.onRepTryActivateAbility(payload.handle, payload.context)
                PacketDistributor.sendToPlayersTrackingEntity(requester, TryActivateAbilityEntityPayload(requester.id, payload.handle, payload.context))
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TryActivateAbilityLocalPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_activate_local"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            AbilityHandle.STREAM_CODEC, TryActivateAbilityLocalPayload::handle,
            ActivationContext.STREAM_CODEC, TryActivateAbilityLocalPayload::context,
            ::TryActivateAbilityLocalPayload
        )
    }

}