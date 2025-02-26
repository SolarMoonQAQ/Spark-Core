package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.component.SkillComponent
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillComponentPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillId: Int,
    val ordinal: Int,
    val data: CompoundTag
): CustomPacketPayload {
    constructor(component: SkillComponent, data: CompoundTag = CompoundTag()): this(component.skill.holder.syncerType, component.skill.holder.syncData, component.skill.id, component.ordinal, data)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInBothSide(payload: SkillComponentPayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = host.getSkill(payload.skillId) ?: return
            skill.components.elementAtOrNull(payload.ordinal)?.sync(payload.data, context)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillComponentPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_component"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillComponentPayload::syncerType,
            SyncData.STREAM_CODEC, SkillComponentPayload::syncData,
            ByteBufCodecs.INT, SkillComponentPayload::skillId,
            ByteBufCodecs.INT, SkillComponentPayload::ordinal,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, SkillComponentPayload::data,
            ::SkillComponentPayload
        )
    }
}