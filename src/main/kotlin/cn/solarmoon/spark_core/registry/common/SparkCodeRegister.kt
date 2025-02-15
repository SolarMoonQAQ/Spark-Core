package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.node.leaves.AnimSpeedModifierComponent
import cn.solarmoon.spark_core.skill.node.leaves.AttackDamageModifierComponent
import cn.solarmoon.spark_core.skill.node.leaves.CameraShakeComponent
import cn.solarmoon.spark_core.skill.node.leaves.InvincibilityComponent
import cn.solarmoon.spark_core.skill.node.leaves.MoveSetComponent
import cn.solarmoon.spark_core.skill.node.leaves.PlayAnimationNode
import cn.solarmoon.spark_core.skill.node.leaves.PreInputReleaseComponent
import cn.solarmoon.spark_core.skill.node.leaves.PreventLocalInputComponent
import cn.solarmoon.spark_core.skill.node.leaves.PreventYRotComponent
import cn.solarmoon.spark_core.skill.node.leaves.SummonShadowComponent
import cn.solarmoon.spark_core.skill.node.leaves.collision.BoxAroundHolderComponent
import cn.solarmoon.spark_core.skill.node.leaves.collision.BoxFollowAnimatedBoneComponent
import cn.solarmoon.spark_core.skill.condition.HoldItemCondition
import cn.solarmoon.spark_core.skill.node.bases.ParallelNode
import cn.solarmoon.spark_core.skill.node.bases.SelectorNode
import cn.solarmoon.spark_core.skill.node.bases.SequenceNode
import cn.solarmoon.spark_core.skill.node.leaves.BehaviorTreeEndNode
import cn.solarmoon.spark_core.skill.node.leaves.BehaviorTreeRefreshNode
import cn.solarmoon.spark_core.skill.node.leaves.EmptyNode
import cn.solarmoon.spark_core.skill.node.leaves.EndChildrenNode
import cn.solarmoon.spark_core.skill.node.leaves.EndSkillNode
import cn.solarmoon.spark_core.skill.node.leaves.KnockBackNode
import cn.solarmoon.spark_core.sync.IntSyncData
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object SparkCodeRegister {

    private fun reg(event: RegisterEvent) {
        event.register(SparkRegistries.SKILL_CONDITION_CODEC.key(), id("hold_item")) { HoldItemCondition.CODEC }

        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("empty_success")) { EmptyNode.Success.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("empty_running")) { EmptyNode.Running.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("empty_failure")) { EmptyNode.Failure.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("sequence")) { SequenceNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("selector")) { SelectorNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("parallel")) { ParallelNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("end_skill")) { EndSkillNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("end_children")) { EndChildrenNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("refresh_root")) { BehaviorTreeRefreshNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("ebd_root")) { BehaviorTreeEndNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("box_around_holder")) { BoxAroundHolderComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("box_follow_animated_bone")) { BoxFollowAnimatedBoneComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("play_animation")) { PlayAnimationNode.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("preinput_release")) { PreInputReleaseComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("attack_damage_modifier")) { AttackDamageModifierComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("anim_speed_modifier")) { AnimSpeedModifierComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("camera_shake")) { CameraShakeComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("prevent_local_input")) { PreventLocalInputComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("prevent_yrot")) { PreventYRotComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("moveset")) { MoveSetComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("invincibility")) { InvincibilityComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("summon_shadow")) { SummonShadowComponent.CODEC }
        event.register(SparkRegistries.BEHAVIOR_NODE_CODEC.key(), id("knock_back")) { KnockBackNode.CODEC }

        event.register(SparkRegistries.SYNC_DATA_STREAM_CODEC.key(), id("int")) { IntSyncData.STREAM_CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}