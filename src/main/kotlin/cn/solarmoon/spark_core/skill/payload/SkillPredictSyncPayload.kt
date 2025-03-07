package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.SkillType
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
    val skillType: SkillType<*>,
    val clientId: Int,
    val serverId: Int,
    val active: Boolean
): CustomPacketPayload {
    constructor(host: SkillHost, type: SkillType<*>, clientId: Int, serverId: Int, active: Boolean): this(host.syncerType, host.syncData, type, clientId, serverId, active)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillPredictSyncPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return

            // 客户端自己发起的预测（是否启用已在客户端侧发送时决定）
            if (host == context.player()) {
                val skill = host.predictedSkills.remove(payload.clientId) ?: return
                skill.id = payload.serverId
                host.allSkills[skill.id] = skill
            }
            // 其他客户端的预测需要新建
            else {
                val skill = payload.skillType.createSkillWithoutSync(payload.serverId, host, level)
                if (payload.active) skill.activate()
            }

        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillPredictSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_predict_sync"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillPredictSyncPayload::syncerType,
            SyncData.STREAM_CODEC, SkillPredictSyncPayload::syncData,
            SkillType.STREAM_CODEC, SkillPredictSyncPayload::skillType,
            ByteBufCodecs.INT, SkillPredictSyncPayload::clientId,
            ByteBufCodecs.INT, SkillPredictSyncPayload::serverId,
            ByteBufCodecs.BOOL, SkillPredictSyncPayload::active,
            ::SkillPredictSyncPayload
        )
    }
}