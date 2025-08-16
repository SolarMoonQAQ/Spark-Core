package cn.solarmoon.spark_core.animation.anim.play.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.util.minus
import cn.solarmoon.spark_core.util.plus
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3f

class BlendSpace(
    private val layers: Map<ResourceLocation, AnimationLayer>
) {

    /**
     * 按当前空间权重混合指定骨骼的动画，并输出新的混合结果
     */
    fun blendBone(boneName: String, animatable: IAnimatable<*>): KeyAnimData {
        val pos = Vector3f()
        val rot = Quaterniond()
        val scale = Vector3f(1f)
        layers.values.sortedBy { it.priority }.forEach { layer ->
//            // 跳过无任何动画层
//            if (!layer.isInTransition && layer.animation == null) return@forEach
//            // 跳过不包含当前骨骼姿势层（即该动画中没有提及该骨骼），防止未出现的骨骼以默认0值覆盖了上层动画
//            if (layer.animation != null && layer.animation!!.origin.getBoneAnimation(boneName) == null) return@forEach
            if (!layer.isPlaying) return@forEach
            val bonePose = layer.getBonePose(boneName, animatable)
            val weight = layer.getBoneWeight(boneName)
            if (weight == 0.0) return@forEach
            when(layer.data.blendMode) {
                BlendMode.OVERRIDE -> {
                    pos.lerp(bonePose.position.toVector3f(), weight.toFloat())
                    rot.slerp(Quaterniond().rotateZYX(bonePose.rotation.z, bonePose.rotation.y, bonePose.rotation.x), weight)
                    scale.lerp(bonePose.scale.toVector3f(), weight.toFloat())
                }
                BlendMode.ADDITIVE -> {
                    pos.add(bonePose.position.toVector3f())
                    rot.mul(Quaterniond().rotateZYX(bonePose.rotation.z, bonePose.rotation.y, bonePose.rotation.x))
                    // 最终缩放 = 下层缩放 × [1 + 权重 × (Additive缩放 - 1)]
                    scale.mul((Vec3(1.0, 1.0, 1.0) + (bonePose.scale - Vec3(1.0, 1.0, 1.0)).multiply(Vec3(weight, weight, weight))).toVector3f())
                }
            }
        }
        return KeyAnimData(pos.toVec3(), rot.toEuler().toVec3(), scale.toVec3())
    }

}