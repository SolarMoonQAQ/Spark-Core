package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.play.AnimApplier
import cn.solarmoon.spark_core.animation.state.CommonAnimApplier
import cn.solarmoon.spark_core.animation.presets.DynamicStateAnimApplier
import cn.solarmoon.spark_core.animation.state.PlayerBaseAnimStateMachine
import cn.solarmoon.spark_core.animation.state.PoseAnimApplier
import cn.solarmoon.spark_core.animation.state.UseAnimApplier
import cn.solarmoon.spark_core.animation.vanilla.BoneModifier
import cn.solarmoon.spark_core.camera.CameraAdjuster
import cn.solarmoon.spark_core.ik.presets.PlayerIKPresets
import cn.solarmoon.spark_core.js.SparkJsApplier
import cn.solarmoon.spark_core.physics.collision.CollisionFuncApplier
import cn.solarmoon.spark_core.physics.host.PhysicsHostApplier
import cn.solarmoon.spark_core.physics.level.PhysicsLevelApplier
import cn.solarmoon.spark_core.physics.presets.PresetBodyApplier
import cn.solarmoon.spark_core.preinput.PreInputApplier
import cn.solarmoon.spark_core.skill.SkillApplier
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEventRegister {

    @JvmStatic
    fun register() {
        add(SparkJsApplier)
        add(PhysicsLevelApplier)
        add(PhysicsHostApplier)
        add(PresetBodyApplier)
        add(PlayerIKPresets)
        add(AnimApplier)
        add(PreInputApplier)
        add(CommonAnimApplier)
        add(PlayerBaseAnimStateMachine.Modifier)
        add(UseAnimApplier)
        add(PoseAnimApplier)
        add(DynamicStateAnimApplier)
        add(BoneModifier)
        add(CollisionFuncApplier)
        add(SkillApplier)
        add(CameraAdjuster)
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}