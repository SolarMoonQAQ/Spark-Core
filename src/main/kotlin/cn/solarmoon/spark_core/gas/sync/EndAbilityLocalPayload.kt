package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.ActivationContext
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class EndAbilityLocalPayload(
    val handle: AbilityHandle
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInServer(payload: EndAbilityLocalPayload, context: IPayloadContext) {
            context.enqueueWork {
                val requester = context.player()
                requester.abilitySystemComponent.endAbility(payload.handle)
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<EndAbilityLocalPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_end_local"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            AbilityHandle.STREAM_CODEC, EndAbilityLocalPayload::handle,
            ::EndAbilityLocalPayload
        )
    }

}