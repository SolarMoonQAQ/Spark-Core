package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.component.AnimSpeedModifierComponent
import cn.solarmoon.spark_core.skill.component.AttackDamageModifierComponent
import cn.solarmoon.spark_core.skill.component.CameraShakeComponent
import cn.solarmoon.spark_core.skill.component.InvincibilityComponent
import cn.solarmoon.spark_core.skill.component.MoveSetComponent
import cn.solarmoon.spark_core.skill.component.PlayAnimationComponent
import cn.solarmoon.spark_core.skill.component.PreInputReleaseComponent
import cn.solarmoon.spark_core.skill.component.PreventLocalInputComponent
import cn.solarmoon.spark_core.skill.component.PreventYRotComponent
import cn.solarmoon.spark_core.skill.component.SummonShadowComponent
import cn.solarmoon.spark_core.skill.component.collision.BoxAroundHolderComponent
import cn.solarmoon.spark_core.skill.component.collision.BoxFollowAnimatedBoneComponent
import cn.solarmoon.spark_core.skill.condition.HoldItemCondition
import cn.solarmoon.spark_core.sync.IntSyncData
import cn.solarmoon.spark_core.sync.SyncData
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegisterEvent
import net.neoforged.neoforge.registries.RegistryBuilder

object SparkCodeRegister {

    private fun reg(event: RegisterEvent) {
        event.register(SparkRegistries.SKILL_CONDITION_CODEC.key(), id("hold_item")) { HoldItemCondition.CODEC }

        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("box_around_holder")) { BoxAroundHolderComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("box_follow_animated_bone")) { BoxFollowAnimatedBoneComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("play_animation")) { PlayAnimationComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("preinput_release")) { PreInputReleaseComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("attack_damage_modifier")) { AttackDamageModifierComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("anim_speed_modifier")) { AnimSpeedModifierComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("camera_shake")) { CameraShakeComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("prevent_local_input")) { PreventLocalInputComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("prevent_yrot")) { PreventYRotComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("moveset")) { MoveSetComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("invincibility")) { InvincibilityComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("summon_shadow")) { SummonShadowComponent.CODEC }

        event.register(SparkRegistries.SYNC_DATA_STREAM_CODEC.key(), id("int")) { IntSyncData.STREAM_CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}