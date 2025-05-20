package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

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
    @JvmStatic
    val FALL_FLYING = createStateAnim("fall_flying")
    @JvmStatic
    val SLEEPING = createStateAnim("sleeping")
    @JvmStatic
    val SWIMMING_IDLE = createStateAnim("swimming_idle")
    @JvmStatic
    val SWIMMING = createMoveStateAnim("swimming")

    @JvmStatic
    val VINDICATOR_IDLE_COMBAT = createStateAnim("idle_combat", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VINDICATOR))
    @JvmStatic
    val VINDICATOR_WALK_COMBAT = createStateAnim("walk_combat", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VINDICATOR))

    fun createStateAnim(name: String, index: ResourceLocation = ResourceLocation.withDefaultNamespace("player"), provider: TypedAnimProvider = {}) = SparkCore.REGISTER.typedAnimation()
        .id(name)
        .animIndex(AnimIndex(index, "EntityState/$name"))
        .provider(provider)
        .build()

    fun createCommonAnim(animationId: String, animIndex: AnimIndex, provider: TypedAnimProvider = {}) = SparkCore.REGISTER.typedAnimation()
        .id(animationId)
        .animIndex(animIndex)
        .provider(provider)
        .build()

    fun createMoveStateAnim(name: String, index: ResourceLocation = ResourceLocation.withDefaultNamespace("player"), provider: TypedAnimProvider = {}) = createStateAnim(name, index) {
        onEvent<AnimEvent.Tick> {
            if (holder is Player) {
                speed = holder.getAttributeValue(Attributes.MOVEMENT_SPEED) / (if (holder.isSprinting) 0.13 else 0.1)
                if (holder.isUsingItem) speed /= 1.5
            }
        }
        provider.invoke(this)
    }

}