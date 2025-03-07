package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.component.AddTargetMovementComponent
import cn.solarmoon.spark_core.skill.component.AnimSpeedChangeComponent
import cn.solarmoon.spark_core.skill.component.AttackDamageModifierComponent
import cn.solarmoon.spark_core.skill.component.CameraShakeComponent
import cn.solarmoon.spark_core.skill.component.MovementComponent
import cn.solarmoon.spark_core.skill.component.PhysicsBodyAttackComponent
import cn.solarmoon.spark_core.skill.component.PlayAnimationComponent
import cn.solarmoon.spark_core.skill.component.PlaySkillComponent
import cn.solarmoon.spark_core.skill.component.PlaySoundComponent
import cn.solarmoon.spark_core.skill.component.PreInputReleaseComponent
import cn.solarmoon.spark_core.skill.component.PreventLocalInputComponent
import cn.solarmoon.spark_core.skill.component.PreventYRotComponent
import cn.solarmoon.spark_core.skill.component.SelfKnockBackComponent
import cn.solarmoon.spark_core.skill.component.SummonShadowComponent
import cn.solarmoon.spark_core.skill.component.body_binder.BoxAroundHolderBinder
import cn.solarmoon.spark_core.skill.component.body_binder.BoxFollowAnimatedBoneBinder
import cn.solarmoon.spark_core.sync.IntSyncData
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object SparkCodeRegister {

    private fun reg(event: RegisterEvent) {
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("box_follow_animated_bone")) { BoxFollowAnimatedBoneBinder.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("box_around_holder")) { BoxAroundHolderBinder.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("add_target_movement")) { AddTargetMovementComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("change_anim_speed")) { AnimSpeedChangeComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("attack_damage_modifier")) { AttackDamageModifierComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("shake_camera")) { CameraShakeComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("movement")) { MovementComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("preinput_controller")) { PreInputReleaseComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("prevent_local_input")) { PreventLocalInputComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("prevent_yrot")) { PreventYRotComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("self_knockback")) { SelfKnockBackComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("summon_shadow")) { SummonShadowComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("play_sound")) { PlaySoundComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("play_animation")) { PlayAnimationComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("play_skill")) { PlaySkillComponent.CODEC }
        event.register(SparkRegistries.SKILL_COMPONENT_CODEC.key(), id("physics_body_attack")) { PhysicsBodyAttackComponent.CODEC }

        event.register(SparkRegistries.SYNC_DATA_STREAM_CODEC.key(), id("int")) { IntSyncData.STREAM_CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}