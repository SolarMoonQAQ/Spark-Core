package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.CameraHelper
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.util.minus
import cn.solarmoon.spark_core.util.plus
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3f
import java.util.SortedMap
import java.util.concurrent.ConcurrentSkipListMap

class AnimController(
    val animatable: IAnimatable<*>
) {
    /**
     * 当前 LOD 等级（0-3），由 [physTick] 每 tick 计算更新。
     * Molang 查询 `query.lod` 通过此字段获取。
     */
    var lodLevel: Int = 0
        internal set

    /**
     * LOD 降频跳帧计数器，在 [physTick] 中递增使用。
     */
    var skipCounter: Int = 0
        internal set

    val originAnimations get() = OAnimationSet.getOrEmpty(animatable.modelController.model?.index)
    val originStateMachines get() = OAnimStateMachineSet.getOrEmpty(animatable.modelController.model?.index?.location)

    // 物理线程可在主线程 tick 遍历时创建层，保留组号顺序并允许并发迭代。
    val layers: SortedMap<Int, AnimLayer> = ConcurrentSkipListMap()

    val stateMachines by lazy { originStateMachines.buildRootMachines(animatable) }

    var speedChangeTime = 0
        private set
    var overallSpeed = 1.0f
        private set

    val isPlayingAnim get() = layers.values.any { it.isPlaying }

    /**
     * 在指定时间内改变动画整体速度，时间结束后复原
     */
    fun changeSpeed(time: Int, speed: Float) {
        overallSpeed = speed
        speedChangeTime = time
    }

    fun playAnimation(anim: AnimInstance) {
        layers.computeIfAbsent(anim.group) { AnimLayer() }.addAnimation(anim)
    }

    fun stopAnimation(group: Int) {
        layers[group]?.snapshotAnimations()?.forEach { it.exit() }
    }

    fun stopAllAnimation() {
        layers.values.forEach {
            it.snapshotAnimations().forEach { it.exit() }
        }
    }

    // ==== blendBone 热路径临时对象复用（仅在 physTick 中调用，物理线程串行安全） ====

    /** 骨骼混合累积位移（每帧复用，避免每骨骼分配 Vector3f） */
    private val tmpPos = Vector3f()
    /** 骨骼混合累积旋转（每帧复用） */
    private val tmpRot = Quaterniond()
    /** 骨骼混合累积缩放（每帧复用） */
    private val tmpScale = Vector3f(1f)
    /** 临时四元数，用于每层的 slerp/mul 中间值构造 */
    private val tmpLayerRot = Quaterniond()

    fun blendBone(boneName: String): KeyAnimData {
        val pos = tmpPos.zero()
        val rot = tmpRot.identity()
        val scale = tmpScale.set(1f, 1f, 1f)
        layers.values.forEach { layer ->
            val weight = layer.getBoneWeight(boneName).toDouble()
            if (weight == 0.0) return@forEach // 先查 HashSet.contains，无动画则跳过昂贵的 blendBone
            val boneTransform = layer.blendBone(boneName)
            when(layer.blendMode) {
                BlendMode.OVERRIDE -> {
                    pos.lerp(boneTransform.position.toVector3f(), weight.toFloat())
                    rot.slerp(tmpLayerRot.identity().rotateZYX(boneTransform.rotation.z, boneTransform.rotation.y, boneTransform.rotation.x), weight)
                    scale.lerp(boneTransform.scale.toVector3f(), weight.toFloat())
                }
                BlendMode.ADDITIVE -> {
                    pos.add(boneTransform.position.toVector3f())
                    rot.mul(tmpLayerRot.identity().rotateZYX(boneTransform.rotation.z, boneTransform.rotation.y, boneTransform.rotation.x))
                    // 最终缩放 = 下层缩放 × [1 + 权重 × (Additive缩放 - 1)]
                    scale.mul((Vec3(1.0, 1.0, 1.0) + (boneTransform.scale - Vec3(1.0, 1.0, 1.0)).multiply(Vec3(weight, weight, weight))).toVector3f())
                }
            }
        }
        return KeyAnimData(pos.toVec3(), rot.toEuler().toVec3(), scale.toVec3())
    }

    fun physTick() {
        // ★ 无活跃动画时全跳过：层集合为空，无动画状态需推进，骨骼无需混合
        if (!isPlayingAnim) {
            if (speedChangeTime > 0) speedChangeTime--
            else overallSpeed = 1.0f
            return
        }

        // 始终推进动画时间（保证进度正确）
        layers.values.forEach { it.physicsTick(overallSpeed) }

        // 计算 LOD 等级（animLevel 在物理线程始终非 null）
        val pos = animatable.getRenderPosition(0)
        val fullDetailDist = animatable.modelController.model?.index?.fullDetailDistance ?: 24.0
        lodLevel = CameraHelper.getLodLevel(animatable.animLevel!!, pos, fullDetailDist)

        // LOD 降频：按 lodSkipIntervals 跳帧执行骨骼混合
        // skipCounter 取模保持在 [0, skipInterval] 范围，防溢出
        val skipInterval = lodSkipIntervals[lodLevel.coerceIn(0, 3)]
        if (skipInterval > 0) {
            skipCounter = (skipCounter + 1) % (skipInterval + 1)
            if (skipCounter != 0) {
                // 跳过本帧骨骼混合，但 speedChangeTime 仍需递减
                if (speedChangeTime > 0) speedChangeTime--
                else overallSpeed = 1.0f
                return
            }
        }

        animatable.modelController.model?.let { model ->
            for (bonePose in model.pose.bonePoseList) {
                bonePose.updateInternal(blendBone(bonePose.name))
            }
            // TODO(IK): 增加默认 null 的姿态处理器字段；整份姿态混合后在物理线程直调一次，供 IK 读写本步姿态，再整批提交供主线程发布。
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0f
    }

    companion object {
        /**
         * LOD 降频跳帧间隔表。
         * 索引为 LOD 等级，值为跳过的 tick 数：
         * - LOD 0: 跳过 0 tick（每 tick 混合，60Hz）
         * - LOD 1: 跳过 1 tick（每 2 tick 混合，30Hz）
         * - LOD 2: 跳过 11 tick（每 12 tick 混合，5Hz）
         * - LOD 3: 跳过 29 tick（每 30 tick 混合，2Hz）
         */
        val lodSkipIntervals = intArrayOf(0, 1, 11, 29)
    }

    fun tick() {
        animatable.modelController.model?.let { model ->
            for (bonePose in model.pose.bonePoseList) {
                bonePose.setChanged()
                animatable.onBoneUpdate(bonePose)
            }
        }

        layers.values.forEach { it.tick() }

        stateMachines.values.forEach { it.progress(StateGraphController.DEFAULT_DT) }
    }

}
