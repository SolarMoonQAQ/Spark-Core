package cn.solarmoon.spark_core.animation.anim.play.blend

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BlendSpace {

    val mainAnimMap = ConcurrentHashMap<String, BlendAnimation>()
    val blendAnimMap = ConcurrentHashMap<String, BlendAnimation>()

    val isValid get() = mainAnimMap.isNotEmpty() || blendAnimMap.isNotEmpty()

    fun putMainAnim(anim: BlendAnimation) {
        val same = mainAnimMap.remove(anim.id)
        same?.let {
            it.markedForRemoval()
            blendAnimMap[it.id + UUID.randomUUID().toString()] = it
        }
        mainAnimMap[anim.id] = anim
    }

    fun tryPutBlendAnim(anim: BlendAnimation) {
        if (anim.data.refreshIfExist) putBlendAnim(anim)
        else putBlendIfAbsent(anim)
    }

    fun putBlendAnim(anim: BlendAnimation) {
        val same = blendAnimMap.remove(anim.id)
        same?.let {
            it.markedForRemoval()
            blendAnimMap[it.id + UUID.randomUUID().toString()] = it
        }
        blendAnimMap[anim.id] = anim
    }

    fun putBlendIfAbsent(anim: BlendAnimation) {
        if (blendAnimMap.containsKey(anim.id)) return
        putBlendAnim(anim)
    }

    fun removeBlend(id: String): Boolean {
        val blend = blendAnimMap[id] ?: return false
        blend.markedForRemoval()
        return true
    }

    /**
     * 按当前空间权重混合指定骨骼的动画，并输出新的混合结果
     */
    fun blendBone(boneName: String, animatable: IAnimatable<*>?): KeyAnimData {
        val values = mainAnimMap.values + blendAnimMap.values
        val totalWeight = values.filter { boneName in it.anim.origin.bones }.sumOf { it.currentWeight * it.data.blendMask.getWeight(boneName) }

        // 如果总权重为0，则返回默认姿势（简化计算）
        if (totalWeight <= 0.0) {
            return KeyAnimData()
        }

        val pos = Vector3f()
        val rot = Quaternionf(); var accumulatedWeight = 0f
        val scale = Vector3f(1f)
        values.forEach {
            val boneData = it.anim.origin.getBoneAnimation(boneName) ?: return@forEach
            val pt = (it.currentWeight * it.data.blendMask.getWeight(boneName) / totalWeight).toFloat()
            val time = when (it.anim.origin.loop) {
                Loop.TRUE -> it.anim.time % it.anim.maxLength
                Loop.ONCE -> it.anim.time
                Loop.HOLD_ON_LAST_FRAME -> it.anim.time
            }
            pos.add(boneData.getAnimPosAt(time, animatable).mul(pt))
//            rot.slerp(boneData.getAnimRotAt(time, animatable).toQuaternionf(), pt)
            scale.add(boneData.getAnimScaleAt(time, animatable).mul(pt)).sub(Vector3f(pt))

            // 计算pt和时间
            val origRot = boneData.getAnimRotAt(time, animatable).toQuaternionf()
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

    fun physTick(overallSpeed: Double = 1.0) {
        mainAnimMap.physicsTick(overallSpeed)
        blendAnimMap.physicsTick(overallSpeed)
    }

    fun tick() {
        mainAnimMap.tick()
        blendAnimMap.tick()
    }

    private fun ConcurrentHashMap<String, BlendAnimation>.physicsTick(overallSpeed: Double) {
        values.forEach {
            if (it.transitionState != TransitionState.NONE) it.doTransition()
            else it.anim.physTick(overallSpeed)
        }
        filter { it.value.anim.isCancelled }.map { it.value }.forEach { it.markedForRemoval() }
        filter { it.value.isRemoved }.map { it.key }.forEach { remove(it) }
    }

    private fun ConcurrentHashMap<String, BlendAnimation>.tick() {
        values.forEach {
            if (!it.isInTransition) {
                it.anim.tick()
            }
        }
    }

}