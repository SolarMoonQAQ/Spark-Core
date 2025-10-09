package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage
import cn.solarmoon.spark_core.util.minus
import cn.solarmoon.spark_core.util.plus
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3f
import kotlin.collections.component1
import kotlin.collections.component2

class AnimController(
    val animatable: IAnimatable<*>
) {

    val originAnimations get() = OAnimationSet.getOrEmpty(animatable.modelController.model?.index?.location)
    val originStateMachines get() = OAnimStateMachineSet.getOrEmpty(animatable.modelController.model?.index?.location)

    val layers = mutableMapOf<Int, AnimLayer>()

    val stateMachines by lazy { originStateMachines.animationControllers.mapValues { it.value.build(animatable) } }

    var speedChangeTime: Int = 0
        private set
    var overallSpeed: Double = 1.0
        private set

    /**
     * 临时变量存储，用于存储动画结束后自动销毁的临时变量，格式:t.name或temp.name
     */
    val tempStorage: ITempVariableStorage = VariableStorage()

    /**
     * 作用域变量存储，用于存储动画过程中的变量，格式:v.name或variable.name
     */
    val scopedStorage: IScopedVariableStorage = VariableStorage()

    /**
     * 外置变量存储，用于存储外部传入的变量，格式:c.name……吗？尚不明确
     */
    val foreignStorage: IForeignVariableStorage = VariableStorage()

    val isPlayingAnim get() = layers.values.any { it.isPlaying }

    /**
     * 在指定时间内改变动画整体速度，时间结束后复原
     */
    fun changeSpeed(time: Int, speed: Double) {
        overallSpeed = speed
        speedChangeTime = time
    }

    fun stopAnimation(group: Int) {
        layers[group]?.animations?.forEach { it.exit() }
    }

    fun stopAllAnimation() {
        layers.values.forEach {
            it.animations.forEach { it.exit() }
        }
    }

    fun blendBone(boneName: String): KeyAnimData {
        val pos = Vector3f()
        val rot = Quaterniond()
        val scale = Vector3f(1f)
        layers.entries.sortedBy { it.key }.forEach { (_, layer) ->
//            // 跳过无任何动画层
//            if (!layer.isInTransition && layer.animation == null) return@forEach
//            // 跳过不包含当前骨骼姿势层（即该动画中没有提及该骨骼），防止未出现的骨骼以默认0值覆盖了上层动画
//            if (layer.animation != null && layer.animation!!.origin.getBoneAnimation(boneName) == null) return@forEach
            if (!layer.isPlaying) return@forEach
            val boneTransform = layer.blendBone(boneName)
            val weight = layer.weight
            if (weight == 0.0) return@forEach
            when(layer.blendMode) {
                BlendMode.OVERRIDE -> {
                    pos.lerp(boneTransform.position.toVector3f(), weight.toFloat())
                    rot.slerp(Quaterniond().rotateZYX(boneTransform.rotation.z, boneTransform.rotation.y, boneTransform.rotation.x), weight)
                    scale.lerp(boneTransform.scale.toVector3f(), weight.toFloat())
                }
                BlendMode.ADDITIVE -> {
                    pos.add(boneTransform.position.toVector3f())
                    rot.mul(Quaterniond().rotateZYX(boneTransform.rotation.z, boneTransform.rotation.y, boneTransform.rotation.x))
                    // 最终缩放 = 下层缩放 × [1 + 权重 × (Additive缩放 - 1)]
                    scale.mul((Vec3(1.0, 1.0, 1.0) + (boneTransform.scale - Vec3(1.0, 1.0, 1.0)).multiply(Vec3(weight, weight, weight))).toVector3f())
                }
            }
        }
        return KeyAnimData(pos.toVec3(), rot.toEuler().toVec3(), scale.toVec3())
    }

    fun physTick() {
        layers.forEach { (_, layer) -> layer.physicsTick(overallSpeed) }

        animatable.modelController.model?.let { model ->
            model.origin.bones.forEach { (boneName, bone) ->
                val bonePose = model.pose.getBonePoseOrCreateEmpty(boneName)
                bonePose.updateInternal(blendBone(boneName))
            }
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0
    }

    fun tick() {
        animatable.modelController.model?.let { model ->
            model.origin.bones.forEach {
                model.pose.getBonePoseOrCreateEmpty(it.key).setChanged()
            }
        }

        layers.forEach { (_, layer) -> layer.tick() }

        stateMachines.values.forEach { it.tick() }
    }

}