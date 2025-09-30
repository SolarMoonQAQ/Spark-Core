package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * ### 动画体
 */
interface IAnimatable<T> {

    /**
     * 一般而言输入this即可，用于调用该动画体的持有者
     */
    val animatable: T

    /**
     * 动画体所处的世界
     */
    val animLevel: Level?

    /**
     * 动画控制器，包含了对动画的控制/过渡/混合/获取等控制性操作，在类中新建一个新的即可
     */
    val animController: AnimController

    val modelController: ModelController

    fun getWorldPositionMatrix(partialTicks: Number = 1f): Matrix4f

    /**
     * 当任意骨骼被更新后调用，可以在此基础上对骨骼的位移旋转等参数进行调整
     */
    fun onBoneUpdate(event: BoneUpdateEvent) {}

}