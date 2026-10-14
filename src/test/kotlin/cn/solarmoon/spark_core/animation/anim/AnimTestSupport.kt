package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.InterpolationType
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OBoneAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OKeyFrame
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.Vector3js
import net.minecraft.world.level.Level
import org.joml.Matrix4f

/**
 * 动画测试替身 —— 不依赖 Minecraft 世界、资源包或物理线程。
 *
 * [animLevel] 恒为 null：既让 [AnimInstance.enter] / [AnimInstance.exit] 走
 * 「调用线程直接处理事件」的退化路径（缺陷 A 的用例所需），也让关键帧求值走
 * [cn.solarmoon.spark_core.js.molang.JSMolangValue] 的纯数字常量缓存、不碰持有者。
 */
internal class StubAnimatable : IAnimatable<Unit> {
    override val animatable: Unit = Unit
    override val animLevel: Level? = null
    override val defaultModelIndex: ModelIndex = ModelIndex.EMPTY
    override val variables: MutableMap<String, Any> = mutableMapOf()
    override val animController: AnimController by lazy { AnimController(this) }
    override val modelController: ModelController by lazy { ModelController(this) }

    override fun getWorldPositionMatrix(partialTicks: Number): Matrix4f = Matrix4f()
}

/** 关键帧分量：纯数字字符串走 MolangConstantCache，不触发 Molang 编译 */
private fun js(value: Float): JSMolangValue = JSMolangValue(value.toString())

internal fun keyFrame(x: Float, y: Float = x, z: Float = x): OKeyFrame {
    val pre = Vector3js(js(x), js(y), js(z))
    return OKeyFrame(pre, pre, InterpolationType.LINEAR)
}

/**
 * 构造只含一个 0 秒关键帧的骨骼动画。
 *
 * @param position YSM/Blockbench 原始单位；[OBoneAnimation.getAnimPosAt] 会乘 (-1/16, 1/16, 1/16)。
 *                 想让 blendBone 得到位移 (4,0,0)，传入 (-64, 0, 0)。
 * @param scale    直接就是 blendBone 使用的缩放值。
 */
internal fun bone(
    position: Triple<Float, Float, Float>? = null,
    scale: Float? = null
): OBoneAnimation {
    val positionMap = if (position != null) {
        linkedMapOf(0f to keyFrame(position.first, position.second, position.third))
    } else linkedMapOf()
    val scaleMap = if (scale != null) linkedMapOf(0f to keyFrame(scale)) else linkedMapOf()
    return OBoneAnimation(linkedMapOf(), positionMap, scaleMap)
}

internal fun anim(vararg bones: Pair<String, OBoneAnimation>): OAnimation =
    OAnimation(cn.solarmoon.spark_core.animation.anim.origin.Loop.ONCE, 10f, linkedMapOf(*bones), linkedMapOf())

internal fun instance(holder: IAnimatable<*>, origin: OAnimation, weight: Float = 1f): AnimInstance {
    val inst = AnimInstance(holder, origin)
    inst.currentWeight = weight
    return inst
}

internal fun layerFor(holder: StubAnimatable, group: Int = AnimGroups.POSTURE): AnimLayer =
    holder.animController.layers.getOrPut(group) { AnimLayer() }
