package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Function

interface SkillGroupController {

    val id: Int get() = SparkRegistries.SKILL_GROUP_CONTROLLER_CODEC.getId(codec)

    val registryKey: ResourceLocation get() = SparkRegistries.SKILL_GROUP_CONTROLLER_CODEC.getKey(codec) ?: throw NullPointerException("技能组控制件尚未注册")

    val skills: List<ResourceLocation>

    val codec: MapCodec<out SkillGroupController>

    /**
     * 发送[SkillGroupControlPayload]数据包后可在此进行双端操作
     */
    fun sync(host: SkillHost, data: CompoundTag, context: IPayloadContext) {}

    companion object {
        val CODEC = SparkRegistries.SKILL_GROUP_CONTROLLER_CODEC.byNameCodec()
            .dispatch(
                SkillGroupController::codec,
                Function.identity()
            )
    }

}