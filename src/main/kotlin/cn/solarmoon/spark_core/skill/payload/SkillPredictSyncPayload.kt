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

class SkillPredictSyncPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val clientId: Int,
    val serverId: Int
): CustomPacketPayload {
    constructor(host: SkillHost, clientId: Int, serverId: Int): this(host.syncerType, host.syncData, clientId, serverId)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillPredictSyncPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = host.predictedSkills.remove(payload.clientId) ?: return
            skill.id = payload.serverId
            host.allSkills[skill.id] = skill
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillPredictSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_predict_sync"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillPredictSyncPayload::syncerType,
            SyncData.STREAM_CODEC, SkillPredictSyncPayload::syncData,
            ByteBufCodecs.INT, SkillPredictSyncPayload::clientId,
            ByteBufCodecs.INT, SkillPredictSyncPayload::serverId,
            ::SkillPredictSyncPayload
        )
    }
}