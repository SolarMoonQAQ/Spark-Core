package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

typealias TypedAnimProvider = AnimInstance.() -> Unit

/**
 * ### 类型化动画
 * > 拥有固定的动画指向、双端一致的动画id、固定的动画实例行为。其主要目的是对一些动画进行行为预设或是为了进行最简动画同步。
 *
 * *此类型**需要注册**，可通过[cn.solarmoon.spark_core.entry_builder.ObjectRegister.typedAnimation]*
 */
class TypedAnimation(
    val index: AnimIndex,
    private val provider: TypedAnimProvider
) {

    val id get() = SparkRegistries.TYPED_ANIMATION.getId(this)
    val registryKey get() = SparkRegistries.TYPED_ANIMATION.getKey(this) ?: throw NullPointerException("类型动画 $index 尚未注册，请在注册后调用此方法。")

    fun exist(animatable: IAnimatable<*>? = null) =
        if (animatable == null) OAnimationSet.get(index.index).getAnimation(index.name) != null
        else animatable.animations.hasAnimation(index.name)

    fun create(animatable: IAnimatable<*>, fromAnimatable: Boolean = false): AnimInstance {
        return if (fromAnimatable) AnimInstance.create(animatable, index.name) {
            provider.invoke(this)
        } else AnimInstance.create(animatable, index) {
            provider.invoke(this)
        }
    }

    fun play(animatable: IAnimatable<*>, transTime: Int, fromAnimatable: Boolean = false) {
        animatable.animController.setAnimation(create(animatable, fromAnimatable), transTime)
    }

    fun syncToClient(id: Int, transTime: Int, fromAnimatable: Boolean = false, exceptPlayer: ServerPlayer? = null) {
        exceptPlayer?.let {
            PacketDistributor.sendToPlayersNear(it.serverLevel(), exceptPlayer, exceptPlayer.x, exceptPlayer.y, exceptPlayer.z, 512.0, TypedAnimPayload(id, this.id, transTime, fromAnimatable))
        } ?: run {
            PacketDistributor.sendToAllPlayers(TypedAnimPayload(id, this.id, transTime, fromAnimatable))
        }
    }

    fun syncToServer(id: Int, transTime: Int, fromAnimatable: Boolean = false) {
        PacketDistributor.sendToServer(TypedAnimPayload(id, this.id, transTime, fromAnimatable))
    }

    // 缓存计算的哈希值，避免每次都调用 getId
    private val cachedHashCode = System.identityHashCode(this)

    override fun equals(other: Any?): Boolean {
        // 使用对象引用相等而不是 ID 相等
        // 这避免了循环调用 getId
        return other === this
    }

    override fun hashCode(): Int {
        // 使用预计算的哈希值，避免调用 getId
        return cachedHashCode
    }

}