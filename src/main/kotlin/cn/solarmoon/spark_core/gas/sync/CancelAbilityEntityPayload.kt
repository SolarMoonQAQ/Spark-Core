package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.AbilitySpec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class CancelAbilityEntityPayload(
    val entityId: Int,
    val handle: AbilityHandle
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: CancelAbilityEntityPayload, context: IPayloadContext) {
            context.enqueueWork {
                val entity = context.player().level().getEntity(payload.entityId) ?: return@enqueueWork
                entity.abilitySystemComponent.cancelAbility(payload.handle)
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<CancelAbilityEntityPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_cancel_entity"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CancelAbilityEntityPayload::entityId,
            AbilityHandle.STREAM_CODEC, CancelAbilityEntityPayload::handle,
            ::CancelAbilityEntityPayload
        )
    }

}