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

class SkillRejectPayload private constructor(
    val syncerType: SyncerType<*>,
    val syncData: SyncData,
    val clientId: Int,
    val reason: String
): CustomPacketPayload {
    constructor(host: SkillHost, clientId: Int, reason: String): this(host.syncerType, host.syncData, clientId, reason)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillRejectPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level()
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return@enqueueWork

                // 结束预测技能（触发End事件与清理）
                val skill = host.predictedSkills[payload.clientId] ?: return@enqueueWork
                skill.end()
                // 调试期提示原因
                SparkCore.LOGGER.error("$host 的技能 ${skill.type.registryKey} 在服务端启用失败： ${payload.reason}")
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillRejectPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_reject"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillRejectPayload::syncerType,
            SyncData.STREAM_CODEC, SkillRejectPayload::syncData,
            ByteBufCodecs.INT, SkillRejectPayload::clientId,
            ByteBufCodecs.STRING_UTF8, SkillRejectPayload::reason,
            ::SkillRejectPayload
        )
    }
}
