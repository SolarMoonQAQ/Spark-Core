package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.sync.TypedAnimPayload
import cn.solarmoon.spark_core.registry.common.SparkRegistries
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
    val name: String,
    private val provider: TypedAnimProvider
) {

    val id get() = SparkRegistries.TYPED_ANIMATION.getId(this)
    val registryKey get() = SparkRegistries.TYPED_ANIMATION.getKey(this) ?: throw NullPointerException("类型动画 $name 尚未注册，请在注册后调用此方法。")

    fun create(animatable: IAnimatable<*>) = animatable.newAnimInstance(name).apply { provider.invoke(this) }

    fun play(animatable: IAnimatable<*>, transTime: Int) {
        if (animatable.animations.hasAnimation(name)) animatable.animController.setAnimation(this, transTime)
    }

    fun syncToClient(id: Int, transTime: Int, exceptPlayer: ServerPlayer? = null) {
        exceptPlayer?.let {
            PacketDistributor.sendToPlayersNear(it.serverLevel(), exceptPlayer, exceptPlayer.x, exceptPlayer.y, exceptPlayer.z, 512.0, TypedAnimPayload(id, this.id, transTime))
        } ?: run {
            PacketDistributor.sendToAllPlayers(TypedAnimPayload(id, this.id, transTime))
        }
    }

    fun syncToServer(id: Int, transTime: Int) {
        PacketDistributor.sendToServer(TypedAnimPayload(id, this.id, transTime))
    }

    override fun equals(other: Any?): Boolean {
        return (other as? TypedAnimation)?.id == id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}