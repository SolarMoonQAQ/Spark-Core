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
        if (animations.size == 1) return animations.maxOf { it.currentWeight }
        // 多实例层：层权重必须与 [blendBone] 的层内分母同源，即「含该骨骼的参与实例权重和」，封顶 1。
        // 恒返回 1.0f 时，层内分母（旧实现为全部实例权重和）与层权重不同源，参与实例会被压小或放大。
        var participatingWeight = 0f
        for (anim in animations) {
            if (anim.origin.bones.containsKey(boneName)) participatingWeight += anim.currentWeight
        }
        return participatingWeight.coerceAtMost(1f)
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
        val boneBaseRot = tmpBoneBaseRot.identity()

        val pos = tmpPos.zero()
        val rot = boneBaseRot; var participatingWeight = 0f
        // tmpScale 在此充当「缩放增量 Σ((scale_i − 1)·w_i)」的累加器，循环后原地归一化为最终缩放
        val scaleDelta = tmpScale.zero()
        animations.forEach {
            val boneData = it.origin.getBoneAnimation(boneName) ?: return@forEach
            val w = it.currentWeight
            val time = it.typedTime
            pos.add(boneData.getAnimPosAt(time, it).mul(w))
            scaleDelta.add(boneData.getAnimScaleAt(time, it).mul(w)).sub(tmpScaleSub.set(w, w, w))

            // 旋转按参与实例的原始权重归一（与位置/缩放同源，不变量）
            val origRot = boneData.getAnimRotAt(time, it).toQuaternionf()
            if (participatingWeight == 0f) {
                rot.set(origRot)
            } else {
                val t = w / (participatingWeight + w)
                rot.slerp(origRot, t)
            }
            participatingWeight += w
        }

        // 层内分母 = 含该骨骼的参与实例权重和（不再是「全部实例权重和」）。
        // 进度由 AnimController 取用的层权重 [getBoneWeight] 用同一个和承担，故不与层内归一互相抵消。
        if (participatingWeight <= 0f) {
            return KeyAnimData()
        }
        pos.div(participatingWeight)
        return KeyAnimData(
            pos.toVec3(),
            rot.getEulerAnglesXYZ(tmpEulerOut).toVec3(),
            scaleDelta.div(participatingWeight).add(1f, 1f, 1f).toVec3()
        )
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