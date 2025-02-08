package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillGroupControlPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val controllerId: Int,
    val extraData: CompoundTag
): CustomPacketPayload {
    constructor(host: SkillHost, controllerId: Int, extraData: CompoundTag = CompoundTag()): this(host.syncerType, host.syncData, controllerId, extraData)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInBothSide(payload: SkillGroupControlPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val group = host.activeSkillGroup ?: return
            group.controllers.forEach { if (it.id == payload.controllerId) it.sync(host, payload.extraData, context) }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillGroupControlPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_component"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(SparkRegistries.SYNCER_TYPE.key()), SkillGroupControlPayload::syncerType,
            SyncData.STREAM_CODEC, SkillGroupControlPayload::syncData,
            ByteBufCodecs.INT, SkillGroupControlPayload::controllerId,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, SkillGroupControlPayload::extraData,
            ::SkillGroupControlPayload
        )
    }
}