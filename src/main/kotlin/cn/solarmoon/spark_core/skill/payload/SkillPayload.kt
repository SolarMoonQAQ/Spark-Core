package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val id: Int,
    val data: CompoundTag
): CustomPacketPayload {
    constructor(skill: Skill, data: CompoundTag = CompoundTag()): this(skill.holder.syncerType, skill.holder.syncData, skill.id, data)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInBothSide(payload: SkillPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = host.getSkill(payload.id) ?: return
            skill.sync(payload.data, context)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillPayload::syncerType,
            SyncData.STREAM_CODEC, SkillPayload::syncData,
            ByteBufCodecs.INT, SkillPayload::id,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, SkillPayload::data,
            ::SkillPayload
        )
    }
}