package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap

class AnimLayer {

    val animations = ConcurrentHashMap.newKeySet<AnimInstance>()

    var blendMode = BlendMode.OVERRIDE

    fun getBoneWeight(boneName: String): Float {
        if (animations.all { !it.origin.bones.contains(boneName) }) return 0.0f
        return if (animations.size == 1) animations.maxOf { it.currentWeight } else 1.0f
    }

    val isPlaying get() = animations.isNotEmpty()

    fun blendBone(boneName: String): KeyAnimData {
        val totalWeight = animations.sumOf { it.currentWeight.toDouble() }
        val boneBaseRot = Quaternionf()

        // 如果总权重为0，则返回默认姿势（简化计算）
        if (totalWeight <= 0.0) {
            return KeyAnimData()
        }

        val pos = Vector3f()
        val rot = boneBaseRot; var accumulatedWeight = 0f
        val scale = Vector3f(1f)
        animations.forEach {
            val boneData = it.origin.getBoneAnimation(boneName) ?: return@forEach
            val pt = (it.currentWeight / totalWeight).toFloat()
            val time = it.typedTime
            pos.add(boneData.getAnimPosAt(time, it).mul(pt))
//            rot.slerp(boneData.getAnimRotAt(time, animatable).toQuaternionf(), pt)
            scale.add(boneData.getAnimScaleAt(time, it).mul(pt)).sub(Vector3f(pt))

            // 计算pt和时间
            val origRot = boneData.getAnimRotAt(time, it).toQuaternionf()
            if (accumulatedWeight == 0f) {
                rot.set(origRot)
                accumulatedWeight = pt
            } else {
                val t = pt / (accumulatedWeight + pt)
                rot.slerp(origRot, t)
                accumulatedWeight += pt
            }
        }
        return KeyAnimData(pos.toVec3(), rot.getEulerAnglesXYZ(Vector3f()).toVec3(), scale.toVec3())
    }

    fun physicsTick(overallSpeed: Float) {
        animations.forEach {
            it.physTick(overallSpeed)
        }

        animations.removeIf { it.state == AnimState.IDLE }
    }

    fun tick() {
        animations.toList().forEach {
            it.tick()
        }
    }

}