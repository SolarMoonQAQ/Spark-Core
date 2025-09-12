package cn.solarmoon.spark_core.animation.anim.play.layer

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.filter

class AnimationLayer(
    val id: ResourceLocation,
    val priority: Int, // 层级优先级，值越高优先级越高
) {

    var data = AnimLayerData()
        private set
    var animation: AnimInstance? = null
        private set

    val animSpaces = ConcurrentHashMap<AnimInstance, AnimSpace>()
    val boneSpaces = ConcurrentHashMap<String, BoneSpace>()

    val isPlaying get() = animSpaces.isNotEmpty()

    fun setAnimation(anim: AnimInstance?, data: AnimLayerData = AnimLayerData()) {
        val currentAnim = animation
        val nextAnim = anim

        if (nextAnim != null) {
            val valid = testAnimValidity(nextAnim)
            if (valid.isNotEmpty()) {
                SparkCore.LOGGER.warn("缺少要播放的动画所需的骨骼: $valid ${nextAnim.holder.animatable} 的动画 ${nextAnim.animIndex} 无法播放")
                return
            }
        }

        if (currentAnim?.rejectNewAnim?.invoke(nextAnim) == true && currentAnim.isCancelled != true) return

        val event = currentAnim?.triggerEvent(AnimEvent.SwitchOut(nextAnim))
        val nextAnimE = event?.nextAnim ?: nextAnim

        this.data = data
        animation = nextAnimE
        animSpaces.forEach { it.key.cancel() }
        animation?.isCancelled = false
        animation?.triggerEvent(AnimEvent.SwitchIn(currentAnim))

        when {
            // 进入
            currentAnim == null && nextAnimE != null -> {
                nextAnimE.origin.bones.keys.forEach {
                    boneSpaces[it] = BoneSpace(it).apply {
                        setWeight(data.boneMask, data.enterTransitionTime)
                    }
                }
                animSpaces[nextAnimE] = AnimSpace(nextAnimE).apply { start(0) }
            }
            // 退出
            currentAnim != null && nextAnimE == null -> {
                boneSpaces.forEach {
                    it.value.setWeight(0.0, data.exitTransitionTime)
                }
            }
            // 切换
            else -> {
                if (nextAnimE != null) {
                    val oldBones = currentAnim?.origin?.bones?.keys ?: emptySet()
                    val newBones = nextAnimE.origin.bones.keys
                    val bonesToReset = oldBones - newBones // 新动画不包含的骨骼

                    // 对新动画包含的骨骼立即加入
                    newBones.forEach {
                        boneSpaces[it] = BoneSpace(it).apply {
                            setWeight(data.boneMask, 0)
                        }
                    }

                    // 对缺失骨骼过渡回默认
                    bonesToReset.forEach {
                        boneSpaces[it] = BoneSpace(it).apply {
                            setWeight(0.0, data.enterTransitionTime)
                        }
                    }

                    // 旧动画开始结束过渡
                    animSpaces.forEach { it.value.end(data.enterTransitionTime) }

                    // 新动画开始进入过渡
                    animSpaces[nextAnimE] = AnimSpace(nextAnimE).apply {
                        start(data.enterTransitionTime)
                    }
                }
            }
        }
    }

    fun stopAnimation(transitionTime: Int = 7) {
        setAnimation(null, data.copy(exitTransitionTime = transitionTime))
    }

    fun setBoneMask(boneMask: BoneMask, transTime: Int = 7) {
        data = data.copy(boneMask = boneMask)
        boneSpaces.forEach { it.value.setWeight(boneMask, transTime) }
    }

    fun getBoneWeight(boneName: String): Double {
        return (boneSpaces[boneName]?.currentWeight ?: 0.0) * data.weight
    }

    fun getBoneTransform(boneName: String, animatable: IAnimatable<*>): KeyAnimData {
        val values = animSpaces.values
        val totalWeight = values.filter { boneName in it.anim.origin.bones }.sumOf { it.currentWeight }
        val boneBaseRot = /*animatable.modelController.originModel.getBone(boneName)?.rotation?.toVector3f()?.toQuaternionf() ?: */Quaternionf()

        // 如果总权重为0，则返回默认姿势（简化计算）
        if (totalWeight <= 0.0) {
            return KeyAnimData()
        }

        val pos = Vector3f()
        val rot = boneBaseRot; var accumulatedWeight = 0f
        val scale = Vector3f(1f)
        values.forEach {
            val boneData = it.anim.origin.getBoneAnimation(boneName) ?: return@forEach
            val pt = (it.currentWeight / totalWeight).toFloat()
            val time = it.anim.typedTime
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

    fun physicsTick(overallSpeed: Double) {
        // 先推进空间时间
        animSpaces.values.forEach { it.physicsTick(overallSpeed) }

        // 自动退出动画，仅对自然完成的动画有效，打断的动画的退出逻辑应在setAnimation中完成
        // 情况A：只剩一个动画空间，且该动画已取消 → 走整层退出（让 BoneSpace 淡出）
        if (animSpaces.size == 1) {
            val only = animSpaces.values.first()
            // 避免重复触发：确保当前 layer 的 animation 还指向这个动画
            if (only.anim.isCancelled && only.transitionState != TransitionState.EXIT && animation == only.anim) {
                // 触发整层退出（BoneSpace 会根据 mask 淡出；AnimSpace 不再做局部 end）
                only.anim.holder.animLevel.submitImmediateTask {
                    stopAnimation(data.exitTransitionTime)
                }
            }
        }
        // 情况B：多个动画空间并存 → 对“已取消但尚未 EXIT”的空间做局部 end
        else if (animSpaces.size > 1) {
            animSpaces.values
                .filter { it.anim.isCancelled && it.transitionState != TransitionState.EXIT }
                .forEach { it.end(data.enterTransitionTime) }
        }

        // 推进骨骼权重过渡
        boneSpaces.forEach { it.value.physicsTick() }

        // 清理已移除空间
        animSpaces.filter { it.value.isRemoved }.keys.toList().forEach { animSpaces.remove(it) }
        if (animation == null && boneSpaces.all { it.value.isRemoved }) {
            animSpaces.clear()
        }
    }

    fun tick() {
        animSpaces.values.forEach {
            it.tick()
        }
    }

    /**
     * @return 当前模型缺少的播放[anim]所需的骨骼的集合，为空则不缺少，即该动画可播放
     */
    fun testAnimValidity(anim: AnimInstance): Set<String> {
        return anim.origin.bones.keys - anim.holder.modelController.originModel.bones.keys
    }

}