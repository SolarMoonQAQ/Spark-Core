package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.AnimResourceKey
import cn.solarmoon.spark_core.animation.CameraHelper
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.util.minus
import cn.solarmoon.spark_core.util.plus
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.resources.ResourceLocation
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

    /**
     * 一次资源绑定：解析出的动画集、控制器集及其根状态机。
     *
     * @property modelType 绑定时模型索引的类型
     * @property modelLocation 绑定时模型索引的位置
     * @property animationKey 绑定时使用的动画集键
     * @property controllerKey 绑定时使用的控制器集键
     * @property animations 动画集解析结果；没有绑定时为 {@code null}
     * @property controllers 控制器集解析结果；没有绑定时为 {@code null}
     * @property controllerAnimations 控制器集引用的片段名；没有控制器集时为空
     * @property rootStateMachines 由控制器集构建的根状态机；没有控制器集时为空
     */
    private class Binding(
        val modelType: String?,
        val modelLocation: ResourceLocation?,
        val animationKey: AnimResourceKey?,
        val controllerKey: AnimResourceKey?,
        val animations: OAnimationSet?,
        val controllers: OAnimStateMachineSet?,
        val controllerAnimations: Set<String>,
        val rootStateMachines: Map<String, AnimStateMachine>,
    )

    /** 当前绑定；首次访问前为 null。游戏线程独占读写。 */
    private var binding: Binding? = null

    /**
     * 当前绑定选定的动画集。未绑定时返回共享空集。
     *
     * 查找按动画体的 [IAnimatable.animationSetKey] 进行：默认键由当前模型索引派生，
     * 覆写该键的动画体可以指向与模型不同名的集合。
     */
    val originAnimations: OAnimationSet
        get() = ensureBinding().animations ?: OAnimationSet.EMPTY

    /**
     * 当前绑定选定的控制器集。未绑定时返回共享空集。
     */
    val originStateMachines: OAnimStateMachineSet
        get() = ensureBinding().controllers ?: OAnimStateMachineSet.EMPTY

    /** 根状态机表；绑定变化时整表替换。 */
    val stateMachines: Map<String, AnimStateMachine>
        get() = ensureBinding().rootStateMachines

    /**
     * 当前绑定的控制器集所拥有的动画片段名（绑定变化时随表重算）。
     *
     * <p>判据与状态机构建同源，见 [OAnimStateMachineSet.referencedAnimationNames]：这些片段由控制器状态
     * 启动与结束，动画体不应对它们做自动播放；其余 `loop:true` 片段归动画体自己播放。
     * 归属只看名字，不看当前有没有控制器在播它——空闲态里没播的片段同一 tick 也可能被状态启动。
     */
    val controllerAnimations: Set<String>
        get() = ensureBinding().controllerAnimations

    // 物理线程可在主线程 tick 遍历时创建层，保留组号顺序并允许并发迭代。
    val layers: SortedMap<Int, AnimLayer> = ConcurrentSkipListMap()

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

    /**
     * 将一次性事件广播给当前绑定的全部根控制器及其激活子控制器。
     *
     * 投递前先做一次绑定检查，因此资源已发布时第一次点击即可被接住，不必先等一个 tick。
     *
     * @param type 事件名
     */
    fun triggerEvent(type: String) {
        ensureBinding().rootStateMachines.values.forEach { it.broadcastEvent(type) }
    }

    /**
     * 游戏线程：确保 [binding] 与模型、两个资源键及其解析结果一致，必要时重建根状态机。
     *
     * 判据是三组值：模型索引的类型与位置、两个资源键、两个已解析定义的对象引用。
     * 只换贴图或 LOD 距离不会重建；加载器整表发布后同一键的新对象会触发重建。
     */
    private fun ensureBinding(): Binding {
        val previous = binding
        if (previous != null && isCurrent(previous)) return previous
        return rebind(previous)
    }

    /**
     * 判断既有绑定是否仍然对应当前模型与已发布资源。
     */
    private fun isCurrent(previous: Binding): Boolean {
        val index = animatable.modelController.model?.index
        if (previous.modelType != index?.type || previous.modelLocation != index?.location) return false
        val animationKey = animatable.animationSetKey
        val controllerKey = animatable.controllerSetKey
        if (previous.animationKey != animationKey || previous.controllerKey != controllerKey) return false
        return OAnimationSet.getOrNull(animationKey) === previous.animations &&
            OAnimStateMachineSet.getOrNull(controllerKey) === previous.controllers
    }

    /**
     * 按当前模型与资源键重新绑定，并从控制器集初态重建根状态机。
     *
     * 先停旧根状态机，让它按正常的退出路径回收自己播放的动画；手动动作和外部控制的层不受影响。
     * 控制器集不可用时不启动任何根状态机。
     *
     * 新绑定先以空状态机表发布，构建完成后再补上状态机表。原因：构建根状态机会同步启动它，
     * `start()` 立即进入初态并执行入口动作，入口动作播放动画时会回头读本控制器的 [originAnimations]。
     * 若此时 [binding] 还是旧值（首次绑定则为 null），那次读取会判定为「需要重绑」并再次构建，
     * 形成无限递归（栈溢出）。以空表先行发布后，嵌套读取看到的就是本次绑定，直接返回。
     */
    private fun rebind(previous: Binding?): Binding {
        val index = animatable.modelController.model?.index
        val animationKey = animatable.animationSetKey
        val controllerKey = animatable.controllerSetKey
        val animations = OAnimationSet.getOrNull(animationKey)
        val controllers = OAnimStateMachineSet.getOrNull(controllerKey)
        val controllerAnimations = controllers?.referencedAnimationNames() ?: emptySet()

        val pending = Binding(
            index?.type, index?.location,
            animationKey, controllerKey,
            animations, controllers, controllerAnimations, emptyMap(),
        )
        binding = pending

        previous?.rootStateMachines?.values?.forEach { it.stop() }
        val rootStateMachines = controllers?.buildRootMachines(animatable) ?: emptyMap()
        val next = Binding(
            index?.type, index?.location,
            animationKey, controllerKey,
            animations, controllers, controllerAnimations, rootStateMachines,
        )
        binding = next
        return next
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
        // 绑定检查与状态机推进同在主线程：资源在这一 tick 之前已发布时，第一次点击/第一帧就能生效。
        val current = ensureBinding()
        animatable.modelController.model?.let { model ->
            for (bonePose in model.pose.bonePoseList) {
                bonePose.setChanged()
                animatable.onBoneUpdate(bonePose)
            }
        }

        layers.values.forEach { it.tick() }

        current.rootStateMachines.values.forEach { it.progress(StateGraphController.DEFAULT_DT) }
    }

}
