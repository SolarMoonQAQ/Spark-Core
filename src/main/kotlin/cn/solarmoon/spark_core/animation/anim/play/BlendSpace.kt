package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.anim.origin.Loop
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import java.util.concurrent.ConcurrentHashMap

class BlendSpace: ConcurrentHashMap<String, BlendAnimation>() {

    fun put(animation: BlendAnimation) {
        put(animation.anim.name, animation)
    }

    fun putIfAbsent(animation: BlendAnimation) {
        putIfAbsent(animation.anim.name, animation)
    }

    /**
     * 按当前空间权重混合指定骨骼的动画，并输出新的混合结果
     */
    fun blendBone(boneName: String): KeyAnimData {
        val totalWeight = values.filter { boneName !in it.boneBlackList && boneName in it.anim.origin.bones }.sumOf { it.weight }
        val pos = Vector3f()
        val rot = Vector3f()
        val scale = Vector3f(1f)
        values.forEach {
            if (boneName in it.boneBlackList) return@forEach
            val boneData = it.anim.origin.getBoneAnimation(boneName) ?: return@forEach
            val pt = (it.weight / totalWeight).toFloat()
            val time = when(it.anim.origin.loop) {
                Loop.TRUE -> it.anim.time % it.anim.maxLength
                Loop.ONCE -> it.anim.time
                Loop.HOLD_ON_LAST_FRAME -> it.anim.time
            }
            pos.add(boneData.getAnimPosAt(time).mul(pt))
            rot.add(boneData.getAnimRotAt(time).mul(pt))
            scale.add(boneData.getAnimScaleAt(time).mul(pt)).sub(Vector3f(pt))
        }
        return KeyAnimData(pos.toVec3(), rot.toVec3(), scale.toVec3())
    }

    fun physTick(overallSpeed: Double = 1.0) {
        values.forEach { it.anim.physTick(overallSpeed) }
        filter { it.key != "main" && it.value.anim.isCancelled }.map { it.key }.forEach { remove(it) }
    }

}