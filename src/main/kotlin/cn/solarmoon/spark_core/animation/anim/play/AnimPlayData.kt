package cn.solarmoon.spark_core.animation.anim.play

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.model.HumanoidModel
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import org.joml.Vector3f
import kotlin.math.PI

/**
 * ### 动画播放数据，在此间进行动画混合
 *
 * 在动画混合中，为了简化动画的标识，这里以主动画+次要动画的形式存储动画，当在需要的地方查找动画时，以主动画列表进行查找，这样可以保证查找出来的动画不是一个动画列表，从而简化代码逻辑
 */
data class AnimPlayData(
    var mixedAnims: LinkedHashSet<MixedAnimation>,
) {

    fun getMixedBoneAnimRotation(boneName: String, partialTick: Float = 0f): Vector3f {
        val mixed = Vector3f()
        mixedAnims.forEach {
            if (boneName in it.boneBlacklist) return@forEach
            val weight = getMixedWeight(it, boneName, partialTick)
            it.animation.getBoneAnim(boneName)?.getPresentAnimRot(it, partialTick)?.let { rot ->
                if (it.isCancelled) {
                    // 结束的过渡从最短路径进行过渡
                    fun normalizeAngle(angle: Float): Float {
                        val twoPi = (2 * PI).toFloat()
                        var newAngle = angle % twoPi
                        if (newAngle > PI) newAngle -= twoPi
                        if (newAngle < -PI) newAngle += twoPi
                        return newAngle
                    }
                    rot.apply {
                        x = normalizeAngle(x)
                        y = normalizeAngle(y)
                        z = normalizeAngle(z)
                    }
                }
                mixed.add(rot.mul(weight))
            }
        }
        return mixed
    }

    fun getMixedBoneAnimPosition(boneName: String, partialTick: Float = 0f): Vector3f {
        val mixed = Vector3f()
        mixedAnims.forEach {
            if (boneName in it.boneBlacklist) return@forEach
            val weight = getMixedWeight(it, boneName, partialTick)
            it.animation.getBoneAnim(boneName)?.getPresentAnimPos(it, partialTick)?.let { pos ->
                mixed.add(pos.mul(weight))
            }
        }
        return mixed
    }

    fun getMixedBoneAnimScale(boneName: String, partialTick: Float = 0f): Vector3f {
        var mixed = Vector3f(1f)
        var totalWeight = 0f

        mixedAnims.forEach {
            if (boneName in it.boneBlacklist) return@forEach
            val weight = getMixedWeight(it, boneName, partialTick).takeIf { it > 0 } ?: return@forEach
            it.animation.getBoneAnim(boneName)?.getPresentAnimScale(it, partialTick)?.let { scale ->
                // 混合比例
                val mixRatio = weight / (weight + totalWeight)
                mixed.lerp(scale, mixRatio)
                totalWeight += weight
            }
        }

        return mixed
    }

    /**
     * 获取输入动画的指定骨骼在本播放中的权重
     */
    fun getMixedWeight(mixedAnimation: MixedAnimation, boneName: String, partialTick: Float = 0f): Float {
        val totalWeight = mixedAnims.sumOf { if (boneName !in it.boneBlacklist) (it.getWeight(partialTick)).toDouble() else 0.0 }
        var weight = mixedAnimation.getWeight(partialTick)
        return (
                if (totalWeight > 0) weight.toFloat() / totalWeight.toFloat()
                else 0f
                ).coerceIn(0f, 1f)
    }

    /**
     * 获取指定的正在播放的动画，限定level为0，有两个同名动画时返回未被设置为删除的，没有返回null
     * @param filter 额外的过滤条件，因为默认情况动画只要存在列表就会视为正在播放，于是可以通过filter过滤掉一些诸如已被设定为删除的动画
     */
    fun getMixedAnimation(name: String, filter: (MixedAnimation) -> Boolean = {true}): MixedAnimation? {
        return getMixedAnimation(name, 0, filter)
    }

    /**
     * 获取指定的正在播放的动画，有两个同名动画时返回未被设置为删除的，没有返回null
     * @param level 动画层级
     * @param filter 额外的过滤条件，因为默认情况动画只要存在列表就会视为正在播放，于是可以通过filter过滤掉一些诸如已被设定为删除的动画
     */
    fun getMixedAnimation(name: String, level: Int, filter: (MixedAnimation) -> Boolean = {true}): MixedAnimation? {
        val matches = mixedAnims.filter { it.name == name && filter.invoke(it) && it.level == level }.toList()
        return if (matches.size >= 2) matches.firstOrNull { it.isCancelled == false }
        else if (matches.size == 1) matches[0]
        else null
    }

    fun copy(): AnimPlayData {
        val set = linkedSetOf<MixedAnimation>()
        mixedAnims.forEach { set.add(it.copy()) }
        return AnimPlayData(set)
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<AnimPlayData> = RecordCodecBuilder.create {
            it.group(
                MixedAnimation.SET_CODEC.fieldOf("main_anims").forGetter { it.mixedAnims },
            ).apply(it, ::AnimPlayData)
        }

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, AnimPlayData> {
            override fun decode(buffer: RegistryFriendlyByteBuf): AnimPlayData {
                val anims = MixedAnimation.SET_STREAM_CODEC.decode(buffer)
                return AnimPlayData(anims)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: AnimPlayData) {
                MixedAnimation.SET_STREAM_CODEC.encode(buffer, value.mixedAnims)
            }
        }

        @JvmStatic
        val EMPTY get() = AnimPlayData(linkedSetOf())
    }

}
