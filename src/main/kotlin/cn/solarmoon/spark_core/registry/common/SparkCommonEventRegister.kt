package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.play.AnimApplier
import cn.solarmoon.spark_core.animation.presets.DynamicStateAnimApplier
import cn.solarmoon.spark_core.animation.vanilla.BoneModifier
import cn.solarmoon.spark_core.camera.CameraAdjuster
import cn.solarmoon.spark_core.entity.EntityPatchApplier
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.physics.body.CollisionFuncApplier
import cn.solarmoon.spark_core.physics.level.PhysicsLevelApplier
import cn.solarmoon.spark_core.preinput.PreInputApplier
import cn.solarmoon.spark_core.skill.SkillApplier
import cn.solarmoon.spark_core.state_machine.StateMachineApplier
import cn.solarmoon.spark_core.state_machine.presets.PlayerBaseAnimStateMachine
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEventRegister {

    @JvmStatic
    fun register(bus: IEventBus) {
        add(PhysicsLevelApplier)
        add(AnimApplier)
        add(PreInputApplier)
        add(PlayerBaseAnimStateMachine.Modifier)
        add(DynamicStateAnimApplier)
        add(BoneModifier)
        add(CollisionFuncApplier)
        add(SkillApplier)
        add(CameraAdjuster)
        add(EntityPatchApplier)
        add(StateMachineApplier)
        add(SparkPackLoaderApplier)

        bus.addListener(SparkPackLoaderApplier::clientStart)
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }
}