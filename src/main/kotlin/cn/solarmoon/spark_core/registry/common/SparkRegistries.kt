package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.skill.SkillComponent
import cn.solarmoon.spark_core.skill.condition.SkillCondition
import cn.solarmoon.spark_core.skill.SkillGroup
import cn.solarmoon.spark_core.skill.SkillGroupController
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.sync.SyncData
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

object SparkRegistries {

    @JvmStatic
    val TYPED_ANIMATION = SparkCore.REGISTER.registry<TypedAnimation>()
        .id("typed_animation")
        .build { it.sync(true).create() }

    @JvmStatic
    val SKILL_COMPONENT_CODEC = SparkCore.REGISTER.registry<MapCodec<out SkillComponent>>()
        .id("skill_component_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val SKILL_CONDITION_CODEC = SparkCore.REGISTER.registry<MapCodec<out SkillCondition>>()
        .id("skill_condition_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val SKILL_GROUP_CONTROLLER_CODEC = SparkCore.REGISTER.registry<MapCodec<out SkillGroupController>>()
        .id("skill_group_controller_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType>()
        .id("syncer_type")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNC_DATA_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out SyncData>>()
        .id("sync_data_stream_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val SKILL_TYPE = ResourceKey.createRegistryKey<SkillType>(ResourceLocation.fromNamespaceAndPath("skill", "type"))

    @JvmStatic
    val SKILL_GROUP = ResourceKey.createRegistryKey<SkillGroup>(ResourceLocation.fromNamespaceAndPath("skill", "group"))

    @JvmStatic
    fun register() {}

}