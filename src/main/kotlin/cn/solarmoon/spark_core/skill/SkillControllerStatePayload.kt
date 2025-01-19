package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.collections.set

class SkillControllerStatePayload(
    val id: Int,
    val name: String
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SkillControllerStatePayload, context: IPayloadContext) {
            val entity = context.player().level().getEntity(payload.id) ?: return
            if (entity !is ISkillControllerHolder<*>) return
            entity.switchTo(payload.name)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SkillControllerStatePayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "skill_controller_state")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SkillControllerStatePayload> = StreamCodec.composite(
            ByteBufCodecs.INT, SkillControllerStatePayload::id,
            ByteBufCodecs.STRING_UTF8, SkillControllerStatePayload::name,
            ::SkillControllerStatePayload
        )
    }
}