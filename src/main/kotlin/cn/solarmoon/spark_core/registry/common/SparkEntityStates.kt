package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimController
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.registry.common.SparkEntityStates.wukong
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent

object SparkEntityStates {

    @JvmStatic
    fun register() {}

//    @JvmStatic
//    val IDLE = SparkCore.REGISTER.state<Entity>()
//        .id("idle")
//        .condition { true }
//        .build()
//
//    @JvmStatic
//    val WALK = SparkCore.REGISTER.state<Entity>()
//        .id("walk")
//        .condition { it.isMoving() || (it.level().isClientSide && it is LocalPlayer && it.moveCheck()) }
//        .build()
//
//    @JvmStatic
//    val WALK_BACK = SparkCore.REGISTER.state<Entity>()
//        .id("walk_back")
//        .condition { it.isMovingBack() || (it.level().isClientSide && it is LocalPlayer && it.moveBackCheck()) }
//        .build()
//
//    @JvmStatic
//    val SPRINTING = SparkCore.REGISTER.state<Entity>()
//        .id("sprinting")
//        .condition { it.isSprinting }
//        .build()
//
//    @JvmStatic
//    val JUMP = SparkCore.REGISTER.state<Entity>()
//        .id("jump")
//        .condition { it.isJumping() }
//        .build()
//
//    @JvmStatic
//    val PLAYER_FLY = SparkCore.REGISTER.state<Entity>()
//        .id("fly")
//        .condition { it is Player && it.abilities.flying && !it.onGround() }
//        .build()
//
////    @JvmStatic
////    val PLAYER_FLY_MOVE = SparkCore.REGISTER.state<Entity>()
////        .id("fly_move")
////        .condition { PLAYER_FLY.get().getCondition(it) && WALK.get().getCondition(it) }
////        .build()
////
////    @JvmStatic
////    val CROUCHING = SparkCore.REGISTER.state<Entity>()
////        .id("crouching")
////        .condition { it.isCrouching }
////        .build()
////
////    @JvmStatic
////    val CROUCHING_MOVE = SparkCore.REGISTER.state<Entity>()
////        .id("crouching_move")
////        .condition { CROUCHING.get().getCondition(it) && WALK.get().getCondition(it) }
////        .build()
//
//    @JvmStatic
//    val EAT = SparkCore.REGISTER.state<Entity>()
//        .id("eat")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.EAT }
//        .build()
//
//    @JvmStatic
//    val DRINK = SparkCore.REGISTER.state<Entity>()
//        .id("drink")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.DRINK }
//        .build()
//
//    @JvmStatic
//    val SIT = SparkCore.REGISTER.state<Entity>()
//        .id("sit")
//        .condition { it.vehicle != null }
//        .build()
//
//    @JvmStatic
//    val BOW = SparkCore.REGISTER.state<Entity>()
//        .id("bow")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.BOW }
//        .build()
//
//    @JvmStatic
//    val CROSSBOW = SparkCore.REGISTER.state<Entity>()
//        .id("crossbow")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.CROSSBOW }
//        .build()
//
//    @JvmStatic
//    val CROSSBOW_IDLE = SparkCore.REGISTER.state<Entity>()
//        .id("crossbow_idle")
//        .condition { it is LivingEntity && it.isHolding(Items.CROSSBOW) && (CrossbowItem.isCharged(it.mainHandItem) || CrossbowItem.isCharged(it.offhandItem)) }
//        .build()
//
//    @JvmStatic
//    val TOOT_HORN = SparkCore.REGISTER.state<Entity>()
//        .id("toot_horn")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.TOOT_HORN && (it.useItem.getUseDuration(it) - it.useItemRemainingTicks) < 1 }
//        .build()
//
//    @JvmStatic
//    val BRUSH = SparkCore.REGISTER.state<Entity>()
//        .id("brush")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.BRUSH }
//        .build()
//
//    @JvmStatic
//    val SPYGLASS = SparkCore.REGISTER.state<Entity>()
//        .id("spyglass")
//        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.SPYGLASS }
//        .build()
//
//    @JvmStatic
//    val SWIM = SparkCore.REGISTER.state<Entity>()
//        .id("swim")
//        .condition { it.isSwimming }
//        .build()
//
//    @JvmStatic
//    val FALL_FLYING = SparkCore.REGISTER.state<Entity>()
//        .id("fall_flying")
//        .condition { it is LivingEntity && it.isFallFlying }
//        .build()
//
//    @JvmStatic
//    val SWINGING_RIGHT = SparkCore.REGISTER.state<Entity>()
//        .id("swinging_right")
//        .condition { it is LivingEntity && it.swinging && it.swingingArm == InteractionHand.MAIN_HAND }
//        .build()
//
//    @JvmStatic
//    val SWINGING_LEFT = SparkCore.REGISTER.state<Entity>()
//        .id("swinging_left")
//        .condition { it is LivingEntity && it.swinging && it.swingingArm == InteractionHand.OFF_HAND }
//        .build()

    @JvmStatic
    val WUKONG = SparkCore.REGISTER.entityType<wukong>()
        .id("wukong")
        .builder(EntityType.Builder.of(::wukong, MobCategory.MONSTER))
        .build()

    @JvmStatic
    fun at(event: EntityAttributeCreationEvent) {
        event.put(WUKONG.get(), Zombie.createAttributes().build())
    }

    class wukong(entityType: EntityType<wukong>, level: Level): Zombie(entityType, level), IEntityAnimatable<wukong> {
        override val animatable: wukong = this
        override val animController: AnimController = AnimController(this)
        override val bones: BoneGroup = BoneGroup(this)
    }

}