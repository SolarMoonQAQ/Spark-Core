package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes

object SparkTypedAnimations {

    @JvmStatic
    fun register() {}

    @JvmStatic
    val IDLE = createStateAnim("idle")
    @JvmStatic
    val WALK = createMoveStateAnim("walk")
    @JvmStatic
    val WALK_BACK = createMoveStateAnim("walk_back")
    @JvmStatic
    val SPRINTING = createMoveStateAnim("sprinting")
    @JvmStatic
    val FLY = createStateAnim("fly")
    @JvmStatic
    val FLY_MOVE = createStateAnim("fly_move")
    @JvmStatic
    val CROUCHING = createStateAnim("crouching")
    @JvmStatic
    val CROUCHING_MOVE = createMoveStateAnim("crouching_move")
    @JvmStatic
    val FALL = createStateAnim("fall")
    @JvmStatic
    val SIT = createStateAnim("sit")

    fun createStateAnim(name: String, provider: TypedAnimProvider = {}) = SparkCore.REGISTER.typedAnimation()
        .id(name)
        .animName("EntityState/$name")
        .provider(provider)
        .build()

    fun createMoveStateAnim(name: String, provider: TypedAnimProvider = {}) = createStateAnim(name) {
        onTick {
            if (holder is LivingEntity) {
                speed = holder.getAttributeValue(Attributes.MOVEMENT_SPEED) / (if (holder.isSprinting) 0.13 else 0.1)
                if (holder.isUsingItem) speed /= 1.5
            }
        }
        provider.invoke(this)
    }

}