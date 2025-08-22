package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.SkillStartRejectedException
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillPredictPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillType: SkillType<*>,
    val clientId: Int,
    val active: Boolean
): CustomPacketPayload {
    constructor(host: SkillHost, type: SkillType<*>, clientId: Int, active: Boolean): this(host.syncerType, host.syncData, type, clientId, active)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInServer(payload: SkillPredictPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level()
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return@enqueueWork
                val type = payload.skillType

                // 服务端硬校验启动条件
                type.conditions.forEach {
                    try {
                        it.test(host, level)
                    } catch (ex: SkillStartRejectedException) {
                        PacketDistributor.sendToPlayer(
                            context.player() as ServerPlayer,
                            SkillRejectPayload(host, payload.clientId, ex.reason)
                        )
                        return@enqueueWork
                    }
                }

                // 创建正式技能并同步
                val skill = type.createSkillWithoutSync(host.skillCount.incrementAndGet(), host, level)
                if (payload.active) skill.activate()
                PacketDistributor.sendToAllPlayers(
                    SkillPredictSyncPayload(host, type, payload.clientId, skill.id, payload.active)
                )
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillPredictPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_predict"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillPredictPayload::syncerType,
            SyncData.STREAM_CODEC, SkillPredictPayload::syncData,
            SkillType.STREAM_CODEC, SkillPredictPayload::skillType,
            ByteBufCodecs.INT, SkillPredictPayload::clientId,
            ByteBufCodecs.BOOL, SkillPredictPayload::active,
            ::SkillPredictPayload
        )
    }
}