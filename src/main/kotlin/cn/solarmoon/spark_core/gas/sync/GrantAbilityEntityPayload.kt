package cn.solarmoon.spark_core.gas.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilityHandle
import cn.solarmoon.spark_core.gas.AbilitySpec
import cn.solarmoon.spark_core.gas.AbilitySpecType
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class GrantAbilityEntityPayload(
    val entityId: Int,
    val handle: AbilityHandle,
    val specType: AbilitySpecType<*, *>
): CustomPacketPayload {
    constructor(entityId: Int, spec: AbilitySpec<*>): this(entityId, spec.handle, spec.type)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: GrantAbilityEntityPayload, context: IPayloadContext) {
            context.enqueueWork {
                val entity = context.player().level().getEntity(payload.entityId) ?: return@enqueueWork
                entity.abilitySystemComponent.apply {
                    putSpec(payload.specType.create(this).apply { handle = payload.handle })
                }
            }.exceptionally {
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<GrantAbilityEntityPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ability_grant_entity"))

//        @JvmStatic
//        val STREAM_CODEC = StreamCodec.composite(
//            AbilityHandle.STREAM_CODEC, GrantAbilityEntityPayload::handle,
//            ::GrantAbilityEntityPayload
//        )
    }

}