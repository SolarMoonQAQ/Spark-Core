package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.phys.toDegrees
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3

class BlendSpace: HashMap<String, BlendAnimation>() {

    fun put(animation: BlendAnimation) {
        put(animation.name, animation)
    }

    /**
     * 按当前空间权重混合指定骨骼的动画，并输出新的混合结果
     */
    fun blendBone(boneName: String, time: Double): KeyAnimData {
        val totalWeight = values.filter { boneName !in it.boneBlackList && boneName in it.anim.bones }.sumOf { it.weight }
        val pos = Vector3f()
        val rot = Vector3f()
        val scale = Vector3f(1f)
        values.forEach {
            if (boneName in it.boneBlackList) return@forEach
            val boneData = it.anim.getBoneAnimation(boneName) ?: return@forEach
            val pt = (it.weight / totalWeight).toFloat()
            val time = when(it.anim.loop) {
                Loop.TRUE -> (time * it.speed) % it.anim.animationLength
                Loop.ONCE -> time * it.speed
                Loop.HOLD_ON_LAST_FRAME -> time * it.speed
            }
            pos.add(boneData.getAnimPosAt(time).mul(pt))
            rot.add(boneData.getAnimRotAt(time).mul(pt))
            scale.add(boneData.getAnimScaleAt(time).mul(pt)).sub(Vector3f(pt))
        }
        return KeyAnimData(pos.toVec3(), rot.toVec3().toDegrees(), scale.toVec3())
    }

}