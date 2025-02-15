package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillInstanceSyncPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillType: SkillType,
    val serverId: Int,
    val active: Boolean
): CustomPacketPayload {
    constructor(host: SkillHost, type: SkillType, serverId: Int, active: Boolean): this(host.syncerType, host.syncData, type, serverId, active)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillInstanceSyncPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = SkillInstance(payload.serverId, payload.skillType, host, level, payload.skillType.behaviorTree.copy())
            if (payload.active) skill.activate()
            host.allSkills[skill.id] = skill
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillInstanceSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_instance_sync"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillInstanceSyncPayload::syncerType,
            SyncData.STREAM_CODEC, SkillInstanceSyncPayload::syncData,
            SkillType.STREAM_CODEC, SkillInstanceSyncPayload::skillType,
            ByteBufCodecs.INT, SkillInstanceSyncPayload::serverId,
            ByteBufCodecs.BOOL, SkillInstanceSyncPayload::active,
            ::SkillInstanceSyncPayload
        )
    }
}