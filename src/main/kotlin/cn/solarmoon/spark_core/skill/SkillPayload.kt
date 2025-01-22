package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.skill.controller.getSkillController
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class SkillPayload(
    val entityId: Int,
    val skillName: String,
    val extraData: CompoundTag
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> = TYPE

    companion object {
        @JvmStatic
        fun handleInBothSide(payload: SkillPayload, context: IPayloadContext) {
            val entity = context.player().level().getEntity(payload.entityId) ?: return
            entity.getSkillController()?.allSkills?.values?.forEach {
                if (it.name == payload.skillName) it.sync(entity, payload.extraData, context)
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SkillPayload::entityId,
            ByteBufCodecs.STRING_UTF8, SkillPayload::skillName,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, SkillPayload::extraData,
            ::SkillPayload
        )
    }
}