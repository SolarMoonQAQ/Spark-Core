package cn.solarmoon.spark_core.skill.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.component.SkillComponent
import cn.solarmoon.spark_core.skill.payload.SkillInstanceSyncPayload
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.collections.set

class SkillComponentPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillId: Int,
    val componentId: Int,
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
            val component = skill.components[payload.componentId]
            component.sync(host, payload.data, context)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillComponentPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_component_sync"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, SkillComponentPayload::syncerType,
            SyncData.STREAM_CODEC, SkillComponentPayload::syncData,
            ByteBufCodecs.INT, SkillComponentPayload::skillId,
            ByteBufCodecs.INT, SkillComponentPayload::componentId,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, SkillComponentPayload::data,
            ::SkillComponentPayload
        )
    }
}