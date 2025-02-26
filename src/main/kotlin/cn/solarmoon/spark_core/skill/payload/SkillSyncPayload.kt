package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillSyncPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillType: SkillType<*>,
    val serverId: Int,
    val active: Boolean
): CustomPacketPayload {
    constructor(host: SkillHost, type: SkillType<*>, serverId: Int, active: Boolean): this(host.syncerType, host.syncData, type, serverId, active)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillSyncPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = payload.skillType.skill.new(payload.serverId, payload.skillType, host, level)
            if (payload.active) skill.activate()
            host.allSkills[skill.id] = skill
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_sync"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillSyncPayload::syncerType,
            SyncData.STREAM_CODEC, SkillSyncPayload::syncData,
            SkillType.Companion.STREAM_CODEC, SkillSyncPayload::skillType,
            ByteBufCodecs.INT, SkillSyncPayload::serverId,
            ByteBufCodecs.BOOL, SkillSyncPayload::active,
            ::SkillSyncPayload
        )
    }
}