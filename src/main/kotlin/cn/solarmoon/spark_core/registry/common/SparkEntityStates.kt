package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.entity.state.getServerMoveSpeed
import cn.solarmoon.spark_core.entity.state.isFalling
import cn.solarmoon.spark_core.entity.state.isJumping
import cn.solarmoon.spark_core.entity.state.isMoving
import cn.solarmoon.spark_core.entity.state.isMovingBack
import cn.solarmoon.spark_core.entity.state.moveBackCheck
import cn.solarmoon.spark_core.entity.state.moveCheck
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.Items
import net.minecraft.world.item.UseAnim

object SparkEntityStates {

    @JvmStatic
    fun register() {}

    @JvmStatic
    val IDLE = SparkCore.REGISTER.state<Entity>()
        .id("idle")
        .condition { true }
        .build()

    @JvmStatic
    val WALK = SparkCore.REGISTER.state<Entity>()
        .id("walk")
        .condition { it.isMoving() || (it.level().isClientSide && it is LocalPlayer && it.moveCheck()) }
        .build()

    @JvmStatic
    val WALK_BACK = SparkCore.REGISTER.state<Entity>()
        .id("walk_back")
        .condition { it.isMovingBack() || (it.level().isClientSide && it is LocalPlayer && it.moveBackCheck()) }
        .build()

    @JvmStatic
    val SPRINTING = SparkCore.REGISTER.state<Entity>()
        .id("sprinting")
        .condition { it.isSprinting }
        .build()

    @JvmStatic
    val JUMP = SparkCore.REGISTER.state<Entity>()
        .id("jump")
        .condition { it.isJumping() }
        .build()

    @JvmStatic
    val PLAYER_FLY = SparkCore.REGISTER.state<Entity>()
        .id("fly")
        .condition { it is Player && it.abilities.flying && !it.onGround() }
        .build()

    @JvmStatic
    val PLAYER_FLY_MOVE = SparkCore.REGISTER.state<Entity>()
        .id("fly_move")
        .condition { PLAYER_FLY.get().getCondition(it) && WALK.get().getCondition(it) }
        .build()

    @JvmStatic
    val CROUCHING = SparkCore.REGISTER.state<Entity>()
        .id("crouching")
        .condition { it.isCrouching }
        .build()

    @JvmStatic
    val CROUCHING_MOVE = SparkCore.REGISTER.state<Entity>()
        .id("crouching_move")
        .condition { CROUCHING.get().getCondition(it) && WALK.get().getCondition(it) }
        .build()

    @JvmStatic
    val EAT = SparkCore.REGISTER.state<Entity>()
        .id("eat")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.EAT }
        .build()

    @JvmStatic
    val DRINK = SparkCore.REGISTER.state<Entity>()
        .id("drink")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.DRINK }
        .build()

    @JvmStatic
    val SIT = SparkCore.REGISTER.state<Entity>()
        .id("sit")
        .condition { it.vehicle != null }
        .build()

    @JvmStatic
    val BOW = SparkCore.REGISTER.state<Entity>()
        .id("bow")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.BOW }
        .build()

    @JvmStatic
    val CROSSBOW = SparkCore.REGISTER.state<Entity>()
        .id("crossbow")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.CROSSBOW }
        .build()

    @JvmStatic
    val CROSSBOW_IDLE = SparkCore.REGISTER.state<Entity>()
        .id("crossbow_idle")
        .condition { it is LivingEntity && it.isHolding(Items.CROSSBOW) && (CrossbowItem.isCharged(it.mainHandItem) || CrossbowItem.isCharged(it.offhandItem)) }
        .build()

    @JvmStatic
    val TOOT_HORN = SparkCore.REGISTER.state<Entity>()
        .id("toot_horn")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.TOOT_HORN && (it.useItem.getUseDuration(it) - it.useItemRemainingTicks) < 1  }
        .build()

    @JvmStatic
    val BRUSH = SparkCore.REGISTER.state<Entity>()
        .id("brush")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.BRUSH }
        .build()

    @JvmStatic
    val SPYGLASS = SparkCore.REGISTER.state<Entity>()
        .id("spyglass")
        .condition { it is LivingEntity && it.useItem.useAnimation == UseAnim.SPYGLASS }
        .build()

    @JvmStatic
    val SWIM = SparkCore.REGISTER.state<Entity>()
        .id("swim")
        .condition { it.isSwimming }
        .build()

}