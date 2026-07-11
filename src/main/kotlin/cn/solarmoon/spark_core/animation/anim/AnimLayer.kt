package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import org.joml.Quaternionf
import org.joml.Vector3f

class AnimLayer {

    // 所有写操作（add / removeIf）仅在物理线程执行，主线程仅读取。
    // 主线程侧已通过 toList() 快照化，物理线程单线程串行——无需 CHM。
    val animations = LinkedHashSet<AnimInstance>()

    var blendMode = BlendMode.OVERRIDE

    /**
     * 本层所有动画所涉及的骨骼名缓存。
     * <p>
     * null 表示缓存已失效，下次 getBoneWeight 调用时重建。
     * animations 仅物理线程修改（add via processTasks, removeIf via physicsTick），
     * 故在 physicsTick 末尾失效即可。
     */
    private var cachedBoneNames: Set<String>? = null

    fun getBoneWeight(boneName: String): Float {
        // 惰性重建：避免每帧 O(n×m) 扫描 animations.all { bones.contains }
        var boneNames = cachedBoneNames
        if (boneNames == null) {
            boneNames = HashSet(animations.size * 16)  // 预估容量减少 resize
            for (anim in animations) {
                boneNames.addAll(anim.origin.bones.keys)  // bones 是 LinkedHashMap，取其 keySet
            }
            cachedBoneNames = boneNames
        }
        if (boneName !in boneNames) return 0.0f
        return if (animations.size == 1) animations.maxOf { it.currentWeight } else 1.0f
    }

    val isPlaying get() = animations.isNotEmpty()

    // ==== blendBone 热路径临时对象复用（仅在 physTick 中调用，物理线程串行安全） ====

    /** 骨骼混合累积位移（每帧复用） */
    private val tmpPos = Vector3f()
    /** 骨骼混合累积缩放（每帧复用） */
    private val tmpScale = Vector3f(1f)
    /** 混合基旋转（每帧复用） */
    private val tmpBoneBaseRot = Quaternionf()
    /** 欧拉角结果暂存（每帧复用，避免 getEulerAnglesXYZ 内部分配） */
    private val tmpEulerOut = Vector3f()
    /** 缩放 sub 运算的临时 vec3（每帧复用） */
    private val tmpScaleSub = Vector3f()

    fun blendBone(boneName: String): KeyAnimData {
        val totalWeight = animations.sumOf { it.currentWeight.toDouble() }
        val boneBaseRot = tmpBoneBaseRot.identity()

        // 如果总权重为0，则返回默认姿势（简化计算）
        if (totalWeight <= 0.0) {
            return KeyAnimData()
        }

        val pos = tmpPos.zero()
        val rot = boneBaseRot; var accumulatedWeight = 0f
        val scale = tmpScale.set(1f, 1f, 1f)
        animations.forEach {
            val boneData = it.origin.getBoneAnimation(boneName) ?: return@forEach
            val pt = (it.currentWeight / totalWeight).toFloat()
            val time = it.typedTime
            pos.add(boneData.getAnimPosAt(time, it).mul(pt))
//            rot.slerp(boneData.getAnimRotAt(time, animatable).toQuaternionf(), pt)
            scale.add(boneData.getAnimScaleAt(time, it).mul(pt)).sub(tmpScaleSub.set(pt, pt, pt))

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
        return KeyAnimData(pos.toVec3(), rot.getEulerAnglesXYZ(tmpEulerOut).toVec3(), scale.toVec3())
    }

    fun physicsTick(overallSpeed: Float) {
        animations.forEach {
            it.physTick(overallSpeed)
        }

        // 仅当确实有动画被移除时才失效缓存，避免大部分 tick 的无意义重建
        if (animations.removeIf { it.state == AnimState.IDLE }) {
            cachedBoneNames = null
        }
    }

    fun tick() {
        animations.toList().forEach {
            it.tick()
        }
    }

}