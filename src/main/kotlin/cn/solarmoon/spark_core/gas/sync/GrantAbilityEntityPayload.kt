package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.AbilitySpec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class GrantAbilityEntityPayload(
    val entityId: Int,
    val handle: AbilityHandle,
    val spec: AbilitySpec<*>
): CustomPacketPayload {
    constructor(entityId: Int, spec: AbilitySpec<*>): this(entityId, spec.handle, spec)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: GrantAbilityEntityPayload, context: IPayloadContext) {
            context.enqueueWork {
                val entity = context.player().level().getEntity(payload.entityId) ?: return@enqueueWork
                entity.abilitySystemComponent.apply {
                    putSpec(payload.spec.also { it.initialize(this, payload.handle) })
                }
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<GrantAbilityEntityPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_grant_entity"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GrantAbilityEntityPayload::entityId,
            AbilityHandle.STREAM_CODEC, GrantAbilityEntityPayload::handle,
            AbilitySpec.STREAM_CODEC, GrantAbilityEntityPayload::spec,
            ::GrantAbilityEntityPayload
        )
    }

}