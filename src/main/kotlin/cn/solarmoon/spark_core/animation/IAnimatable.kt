package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.AnimController
import cn.solarmoon.spark_core.animation.model.BonePose
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.api.physicsLevel
import cn.solarmoon.spark_core.molang.SparkMolangContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * ### 动画体
 */
interface IAnimatable<T> {

    /**
     * 一般而言输入this即可，用于调用该动画体的持有者
     */
    val animatable: T

    /**
     * 动画体所处的世界
     */
    val animLevel: Level?

    val defaultModelIndex: ModelIndex

    /**
     * 动画控制器，包含了对动画的控制/过渡/混合/获取等控制性操作，在类中新建一个新的即可
     */
    val animController: AnimController

    val modelController: ModelController

    /**
     * ⚠ 仅供读取或物理线程内部修改
     * ⚠ 非物理线程请使用 putVariable
     */
    val variables: MutableMap<String, Any>

    fun putVariable(key: String, value: Any) {
        animLevel?.physicsLevel?.submitImmediateTask {
            variables[key] = value
        } ?: run {
            variables[key] = value
        }
    }

    fun getWorldPositionMatrix(partialTicks: Number = 1f): Matrix4f

    /**
     * 获取用于 LOD 判断的轻量世界坐标。
     * 默认实现从 [getWorldPositionMatrix] 提取 translation（构建完整 Matrix4f），
     * GC 开销较大。子类应覆写为字段直读以降低物理线程压力。
     */
    fun getRenderPosition(partialTicks: Number = 0): Vec3 {
        val mat = getWorldPositionMatrix(partialTicks)
        return Vec3(mat.m30().toDouble(), mat.m31().toDouble(), mat.m32().toDouble())
    }

    /**
     * 单骨骼动画后处理回调（替代已移除的逐骨骼 EventBus）。
     * <p>
     * 由 {@link cn.solarmoon.spark_core.animation.anim.AnimController#tick} 每骨骼每帧直调。
     * 默认空实现；LivingEntity 覆写以接入头部朝向/睡觉姿态修正，
     * 载具等可覆写以接入 IK 骨骼修正。
     *
     * @param bonePose 当前骨骼姿态（{@link BonePose#localTransform} 已更新为本帧动画值，
     *                 {@link BonePose#oLocalTransform} 为上帧值，可读取并覆写前者）
     */
    fun onBoneUpdate(bonePose: BonePose) {}

    /**
     * 返回此动画体专用的 Molang 求值上下文。
     * <p>
     * 实现类可重写以返回带自定义 {@code @QueryBinding} 的上下文子类实例。
     * 默认返回 {@link SparkMolangContext} 实例。
     * <p>
     * 返回的上下文通常应缓存（每个实例一个），避免频繁创建。
     *
     * @see cn.solarmoon.spark_core.molang.MolangContextRegistry
     */
    fun getMolangContext(): SparkMolangContext<*> {
        return SparkMolangContext(this)
    }

    /**
     * 由当前活跃动画控制器设置的动画完成状态。
     * 用于 MoLang 查询 `q.all_animations_finished` 和 `q.any_animation_finished`。
     */
    var controllerAllAnimationsFinished: Boolean
        get() = (variables["__controller_all_animations_finished"] as? Boolean) ?: true
        set(value) { variables["__controller_all_animations_finished"] = value }

    var controllerAnyAnimationFinished: Boolean
        get() = (variables["__controller_any_animation_finished"] as? Boolean) ?: true
        set(value) { variables["__controller_any_animation_finished"] = value }

}