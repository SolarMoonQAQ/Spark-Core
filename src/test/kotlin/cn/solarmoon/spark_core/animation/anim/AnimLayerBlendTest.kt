package cn.solarmoon.spark_core.animation.anim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 缺陷 B 的数值验收用例（§2.4 的 B1–B7）。
 *
 * 这里的 blendBone 指 AnimController.blendBone —— 最终系数 = 层权重 x 层内比例，
 * 验收表里的期望值正是这一层的输出。B7 单独验收 AnimLayer.getBoneWeight 的聚合方式。
 */
class AnimLayerBlendTest {

    /** B1：A 含 X、位置 (4,0,0)、权重 1；B 不含 X、权重 1 → 现状 (2,0,0)，修后 (4,0,0) */
    @Test
    fun b1_positionDenominatorOnlyCountsParticipatingInstances() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        // OBoneAnimation.getAnimPosAt 会乘 (-1/16, 1/16, 1/16)，故原始 -64 对应位移 4
        layer.animations.add(instance(holder, anim("X" to bone(position = Triple(-64f, 0f, 0f))), 1f))
        layer.animations.add(instance(holder, anim("Other" to bone()), 1f))

        val result = holder.animController.blendBone("X")

        assertEquals(4.0, result.position.x, 1e-4)
        assertEquals(0.0, result.position.y, 1e-4)
        assertEquals(0.0, result.position.z, 1e-4)
    }

    /** B2：A、B 都含 X，位置 (4,0,0) / (0,2,0)，权重 1:1 → (2,1,0)，现状与修法一致 */
    @Test
    fun b2_allInstancesContainBoneKeepsWeightedAverage() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        layer.animations.add(instance(holder, anim("X" to bone(position = Triple(-64f, 0f, 0f))), 1f))
        layer.animations.add(instance(holder, anim("X" to bone(position = Triple(0f, 32f, 0f))), 1f))

        val result = holder.animController.blendBone("X")

        assertEquals(2.0, result.position.x, 1e-4)
        assertEquals(1.0, result.position.y, 1e-4)
    }

    /** B3：A 权重 1 含 X=(4,0,0)；B 权重 3 含 X=(0,4,0) → (1,3,0)，两分母相等，现状也对 */
    @Test
    fun b3_allInstancesContainBoneWithUnequalWeights() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        layer.animations.add(instance(holder, anim("X" to bone(position = Triple(-64f, 0f, 0f))), 1f))
        layer.animations.add(instance(holder, anim("X" to bone(position = Triple(0f, 64f, 0f))), 3f))

        val result = holder.animController.blendBone("X")

        assertEquals(1.0, result.position.x, 1e-4)
        assertEquals(3.0, result.position.y, 1e-4)
    }

    /** B4：A 含 X 且 scale=2、权重 1；B 不含 X、权重 1 → 现状 1.5，修后 2 */
    @Test
    fun b4_scaleDenominatorOnlyCountsParticipatingInstances() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        layer.animations.add(instance(holder, anim("X" to bone(scale = 2f)), 1f))
        layer.animations.add(instance(holder, anim("Other" to bone()), 1f))

        val result = holder.animController.blendBone("X")

        assertEquals(2.0, result.scale.x, 1e-4)
    }

    /** B5：新旧互补交叉淡化（wSum<1）—— A 含 X=(4,0,0) 权重 0.25，B 不含 X 权重 0.75 → (1,0,0) */
    @Test
    fun b5_crossFadeLowerSumKeepsProgress() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        layer.animations.add(instance(holder, anim("X" to bone(position = Triple(-64f, 0f, 0f))), 0.25f))
        layer.animations.add(instance(holder, anim("Other" to bone()), 0.75f))

        val result = holder.animController.blendBone("X")

        assertEquals(1.0, result.position.x, 1e-4)
        assertEquals(0.0, result.position.y, 1e-4)
    }

    /** B6：交叉淡化的缩放版 —— A 含 X 且 scale=3、权重 0.25；B 不含 X、权重 0.75 → 1.5 */
    @Test
    fun b6_crossFadeScaleLowerSumKeepsProgress() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        layer.animations.add(instance(holder, anim("X" to bone(scale = 3f)), 0.25f))
        layer.animations.add(instance(holder, anim("Other" to bone()), 0.75f))

        val result = holder.animController.blendBone("X")

        assertEquals(1.5, result.scale.x, 1e-4)
    }

    /** B7：聚合方式锁定为「参与权重求和、封顶 1」—— 两根同时入场到一半 → 1.0 */
    @Test
    fun b7_getBoneWeightSumsParticipatingWeightsCappedAtOne() {
        val holder = StubAnimatable()
        val layer = layerFor(holder)
        layer.animations.add(instance(holder, anim("X" to bone()), 0.5f))
        layer.animations.add(instance(holder, anim("X" to bone()), 0.5f))

        assertEquals(1.0f, layer.getBoneWeight("X"), 1e-6f)
    }
}
