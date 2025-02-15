package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillInstancePredictPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillType: SkillType,
    val clientId: Int,
    val active: Boolean
): CustomPacketPayload {
    constructor(host: SkillHost, type: SkillType, clientId: Int, active: Boolean): this(host.syncerType, host.syncData, type, clientId, active)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInServer(payload: SkillInstancePredictPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val type = payload.skillType
            val skill = SkillInstance(host.skillCount.incrementAndGet(), type, host, level, type.behaviorTree.copy())
            if (payload.active) skill.activate()
            host.allSkills[skill.id] = skill
            PacketDistributor.sendToAllPlayers(SkillInstancePredictSyncPayload(host, payload.clientId, skill.id))
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillInstancePredictPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_instance_predict"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillInstancePredictPayload::syncerType,
            SyncData.STREAM_CODEC, SkillInstancePredictPayload::syncData,
            ByteBufCodecs.registry(SparkRegistries.SKILL_TYPE), SkillInstancePredictPayload::skillType,
            ByteBufCodecs.INT, SkillInstancePredictPayload::clientId,
            ByteBufCodecs.BOOL, SkillInstancePredictPayload::active,
            ::SkillInstancePredictPayload
        )
    }
}