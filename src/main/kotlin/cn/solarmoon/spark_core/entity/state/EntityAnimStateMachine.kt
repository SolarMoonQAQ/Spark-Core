package cn.solarmoon.spark_core.entity.state

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import cn.solarmoon.spark_core.animation.vanilla.asAnimatable
import cn.solarmoon.spark_core.registry.common.SparkEntityStates
import cn.solarmoon.spark_core.state_control.ObjectState
import cn.solarmoon.spark_core.state_control.StateMachine
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.log

class EntityAnimStateMachine(
    entity: Entity
): StateMachine<Entity>(entity) {

    var jumpTick = 0

    val idle = SparkEntityStates.IDLE.get()
    val jump = SparkEntityStates.JUMP.get()
    val walk = SparkEntityStates.WALK.get()
    val walkBack = SparkEntityStates.WALK_BACK.get()
    val sprinting = SparkEntityStates.SPRINTING.get()
    val playerFly = SparkEntityStates.PLAYER_FLY.get()
    val playerFlyMove = SparkEntityStates.PLAYER_FLY_MOVE.get()
    val crouching = SparkEntityStates.CROUCHING.get()
    val crouchingMove = SparkEntityStates.CROUCHING_MOVE.get()
    val sit = SparkEntityStates.SIT.get()
    val eat = SparkEntityStates.EAT.get()
    val drink = SparkEntityStates.DRINK.get()
    val bow = SparkEntityStates.BOW.get()
    val crossBow = SparkEntityStates.CROSSBOW.get()
    val crossBowIdle = SparkEntityStates.CROSSBOW_IDLE.get()
    val horn = SparkEntityStates.TOOT_HORN.get()
    val brush = SparkEntityStates.BRUSH.get()
    val spyglass = SparkEntityStates.SPYGLASS.get()

    val blendAbleAnims = listOf(
        sit,
        playerFlyMove,
        playerFly,
        jump,
        crouchingMove,
        crouching,
        walkBack,
        sprinting,
        walk,
        idle
    )

    val mainStates = listOf(
        eat, drink, bow, spyglass, horn, brush, crossBow, crossBowIdle
    )

    val stateAnimList = mainStates.toMutableList().apply { addAll(blendAbleAnims) }.toList()

    val animMap = buildMap {
        stateAnimList.forEach {
            put(it, "EntityState/${it.name}")
        }
    }

    val nextBlendAble get() = blendAbleAnims.first { it.getCondition(holder) }

    val next get() = stateAnimList.first { it.getCondition(holder) }

    fun getAnimName(state: ObjectState<Entity>) = animMap[state]!!

    fun getTransTime(state: ObjectState<Entity>): Int {
        return if (state in listOf(jump, crouching) || state in mainStates) 0 else 4
    }

    override fun onStateChanged(
        oldState: ObjectState<Entity>?,
        newState: ObjectState<Entity>?
    ) {
        SparkCore.LOGGER.info(oldState?.name + "/" + newState?.name)

        if (newState == null) return
        if (holder !is IEntityAnimatable<*>) return
        val currentAnim = holder.animController.getPlayingAnim()
        if (currentAnim != null && currentAnim.name !in animMap.values) return
        val animName = getAnimName(newState)

        holder.animController.setAnimation(animName, getTransTime(newState)) {
            if (newState in mainStates) it.shouldTurnBody = true
        }
    }

    override fun handleState() {
        var canSet = true

        if (holder is IEntityAnimatable<*>) {
            holder.animController.getPlayingAnim()?.let {
                if (it.name !in animMap.values) {
                    setState(null)
                    return
                }
            }

            currentState?.let { modify(it) }?.let { s ->
                holder.animController.getPlayingAnim()?.takeIf { it.name in animMap.values }?.let {
                    it.speed = s
                }
            }

            when (currentState) {
                jump -> {
                    // 只有不移动时播放完整的跳跃动画，其余情况一旦落地则无缝衔接地面动画
                    if ((!holder.onGround() || nextBlendAble == idle) && !playerFly.getCondition(holder) && next !in mainStates) {
                        if (holder.animController.isPlaying(getAnimName(jump))) canSet = false
                    }
                }

                horn -> {
                    if (holder.animController.isPlaying(getAnimName(horn))) canSet = false
                }
            }
        }

        if (canSet) stateAnimList.any { it.setIfMetCondition(holder, this) }

        if (holder is IEntityAnimatable<*>) {
            currentState?.let { state ->
                when (state) {
                    in mainStates -> {
                        // 混合所有可混合的动画
                        val nextAnimName = getAnimName(nextBlendAble)
                        holder.animController.getPlayingAnim(getAnimName(currentState!!))?.let { anim ->
                            holder.animations.getAnimation(nextAnimName)?.let {
                                if (jumpTick < 0) return@let
                                holder.animController.blendSpace.put(
                                    BlendAnimation("state_mix", it, 1.0, (modify(nextBlendAble) ?: 1.0) / (if (state == crossBowIdle) 1.0 else 2.0), anim.animData.bones.map { it.key })
                                )
                            }
                        }
                    }
                }
            }
        }

        // 由于jump是一瞬间的状态，这里为了维持跳跃混合需要一个tick来保持一定时间
        if (jump.getCondition(holder)) jumpTick = -12
        if (jumpTick < 0) jumpTick++
    }

    fun modify(state: ObjectState<Entity>): Double? {
        var result = 1.0

        if (holder is LivingEntity) {
            when (state) {
                eat, drink -> {
                    result = (20 * 1.6) / holder.useItem.getUseDuration(holder)
                }
                walk, walkBack, crouchingMove -> {
                    result = holder.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1
                }
                sprinting -> {
                    result = holder.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.13
                }
            }
        }

        return result.toDouble()
    }

}