package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillInstancePredictSyncPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val clientId: Int,
    val serverId: Int
): CustomPacketPayload {
    constructor(host: SkillHost, clientId: Int, serverId: Int): this(host.syncerType, host.syncData, clientId, serverId)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillInstancePredictSyncPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = host.predictedSkills.remove(payload.clientId) ?: return
            skill.id = payload.serverId
            host.allSkills[skill.id] = skill
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillInstancePredictSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_instance_predict_sync"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillInstancePredictSyncPayload::syncerType,
            SyncData.STREAM_CODEC, SkillInstancePredictSyncPayload::syncData,
            ByteBufCodecs.INT, SkillInstancePredictSyncPayload::clientId,
            ByteBufCodecs.INT, SkillInstancePredictSyncPayload::serverId,
            ::SkillInstancePredictSyncPayload
        )
    }
}