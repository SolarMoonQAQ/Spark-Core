package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimProvider
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

object SparkTypedAnimations {

    @JvmStatic
    fun register() {}

//    @JvmStatic
//    val LAND_IDLE = createStateAnim("land.idle")
//    @JvmStatic
//    val LAND_MOVE = createMoveStateAnim("land.move")
//    @JvmStatic
//    val LAND_MOVE_BACK = createMoveStateAnim("land.move_back")
//    @JvmStatic
//    val LAND_SPRINT = createMoveStateAnim("land.sprint")
//    @JvmStatic
//    val CROUCH_IDLE = createStateAnim("crouch.idle")
//    @JvmStatic
//    val CROUCH_MOVE = createMoveStateAnim("crouch.move")
//    @JvmStatic
//    val CROUCH_MOVE_BACK = createMoveStateAnim("crouch.move_back")
//    @JvmStatic
//    val FLY_IDLE = createStateAnim("fly.idle")
//    @JvmStatic
//    val FLY_MOVE = createStateAnim("fly.move")
//    @JvmStatic
//    val SWIM_IDLE = createStateAnim("swim.idle")
//    @JvmStatic
//    val SWIM_MOVE = createMoveStateAnim("swim.move")
//
//    @JvmStatic
//    val FALL = createStateAnim("fall")
//    @JvmStatic
//    val SIT = createStateAnim("sit")
//    @JvmStatic
//    val FALL_FLYING = createStateAnim("fall_fly")
//    @JvmStatic
//    val SLEEP = createStateAnim("sleep")
//    @JvmStatic
//    val JUMP_LAND = createStateAnim("jump_land")
//
//    fun createStateAnim(name: String, index: ResourceLocation = SparkResourcePathBuilder.buildAnimationPath("spark_core", "spark_core", "player", "base_state"), provider: TypedAnimProvider = {}) = SparkCore.REGISTER.typedAnimation()
//        .id(name)
//        .animIndex(AnimIndex(index, "state.$name"))
//        .provider(provider)
//        .build()
//
//
//    fun createMoveStateAnim(name: String, index: ResourceLocation = SparkResourcePathBuilder.buildAnimationPath("spark_core", "spark_core", "player", "base_state"), provider: TypedAnimProvider = {}) = createStateAnim(name, index) {
//        onEvent<AnimEvent.Tick> {
//            if (holder is Player) {
//                speed = holder.getAttributeValue(Attributes.MOVEMENT_SPEED) / (if (holder.isSprinting) 0.13 else 0.1)
//                if (holder.isUsingItem) speed /= 1.5
//            }
//        }
//        provider.invoke(this)
//    }

}