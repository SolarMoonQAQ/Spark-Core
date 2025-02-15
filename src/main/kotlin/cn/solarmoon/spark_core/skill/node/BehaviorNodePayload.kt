package cn.solarmoon.spark_core.skill.node

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class BehaviorNodePayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val skillId: Int,
    val nodeOrdinal: Int,
    val extraData: CompoundTag
): CustomPacketPayload {
    constructor(node: BehaviorNode, extraData: CompoundTag = CompoundTag()): this(node.skill.holder.syncerType, node.skill.holder.syncData, node.skill.id, node.ordinal, extraData)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInBothSide(payload: BehaviorNodePayload, context: IPayloadContext) {
            val level = context.player().level()
            val host = payload.syncerType.getSyncer(level, payload.syncData) as? SkillHost ?: return
            val skill = host.allSkills[payload.skillId] ?: return
            val node = skill.behaviorTree.allNodes.getOrNull(payload.nodeOrdinal) ?: return
            node.sync(host, payload.extraData, context)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<BehaviorNodePayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "behavior_node"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(SparkRegistries.SYNCER_TYPE.key()), BehaviorNodePayload::syncerType,
            SyncData.STREAM_CODEC, BehaviorNodePayload::syncData,
            ByteBufCodecs.INT, BehaviorNodePayload::skillId,
            ByteBufCodecs.INT, BehaviorNodePayload::nodeOrdinal,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, BehaviorNodePayload::extraData,
            ::BehaviorNodePayload
        )
    }
}