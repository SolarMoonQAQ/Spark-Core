package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.AbilitySpec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class EndAllAbilitiesEntityPayload(
    val entityId: Int
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: EndAllAbilitiesEntityPayload, context: IPayloadContext) {
            context.enqueueWork {
                val entity = context.player().level().getEntity(payload.entityId) ?: return@enqueueWork
                entity.abilitySystemComponent.onRepEndAllAbilities()
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<EndAllAbilitiesEntityPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_end_all_entity"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, EndAllAbilitiesEntityPayload::entityId,
            ::EndAllAbilitiesEntityPayload
        )
    }

}