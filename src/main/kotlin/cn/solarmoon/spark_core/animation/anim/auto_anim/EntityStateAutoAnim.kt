package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation
import cn.solarmoon.spark_core.entity.state.EntityState
import cn.solarmoon.spark_core.entity.state.getServerMoveSpeed
import cn.solarmoon.spark_core.entity.state.getState
import net.minecraft.world.entity.Entity
import kotlin.math.log

class EntityStateAutoAnim(
    entity: Entity,
    animatable: IEntityAnimatable<*>
): EntityAutoAnim(entity, animatable, "EntityState") {

    override val shouldTurnBody: Boolean = false

    lateinit var playData: Map<EntityState, String>

    init {
        resetPlayData()
    }

    fun resetPlayData() {
        playData = buildMap {
            EntityState.entries.forEach {
                put(it, "$prefix/${it.getName()}")
            }
        }
    }

    override fun getAllAnimNames(): Set<String> {
        return playData.values.toSet()
    }

    fun getState() = entity.getState()

    override fun isSinglePlay(anim: MixedAnimation): Boolean {
        return getState() == EntityState.JUMP
    }

    fun getAnimation(state: EntityState): MixedAnimation {
        val modelPath = animatable.animData.modelPath
        return when(state) {
            EntityState.JUMP -> MixedAnimation(modelPath, playData[state]!!, _startTransSpeed = 6f)
            else -> MixedAnimation(modelPath, playData[state]!!)
        }
    }

    override fun getAnimation(): MixedAnimation {
        return getAnimation(getState())
    }

    override fun isValid(): Boolean {
        val mixAnimations = animatable.animData.playData.mixedAnims
        val isPlayingOtherAnim = !mixAnimations.isEmpty && mixAnimations.any { it.name !in getAllAnimNames() && !it.isCancelled }
        val jump = animatable.animData.playData.getMixedAnimation(playData[EntityState.JUMP]!!)
        return !isPlayingOtherAnim && (jump == null || jump.isTickIn(0.4, jump.maxTick))
    }

    override fun getAnimSuffixName(): String {
        return playData[getState()]!!.substringAfter("/")
    }

    override fun tick() {
        if (tryPlay({ getState() == EntityState.JUMP || it.name != playData[EntityState.JUMP]!! })) {
            modify()
        }
    }

    fun modify() {
        if (entity !is IEntityAnimatable<*>) return
        val factor = when(getState()) {
            EntityState.WALK -> 1f / 215
            EntityState.WALK_BACK -> 1f / 215
            EntityState.SPRINTING -> 1f / 280
            else -> 0f
        }

        val fc = when(getState()) {
            EntityState.WALK -> 3f
            EntityState.WALK_BACK -> 3f
            EntityState.SPRINTING -> 20f
            else -> 0f
        }

        // 调整结果并使用对数函数来平滑曲线
        var result = entity.getServerMoveSpeed() * factor * 1000
        if (result > 1f) result = log(result, fc) + 1f

        if (factor != 0f) {
            entity.animData.playData.mixedAnims.filter { it.name == getAnimName() }.forEach {
                it.speed = result.coerceAtLeast(0.5f)
            }
        }
    }

    fun blendWithoutArms(includeIdle: Boolean, stopFilter: (MixedAnimation) -> Boolean) {
        if (!includeIdle && getState() == EntityState.IDLE) {
            animatable.animController.stopAllAnimation { it.level == 1 }
            return
        }
        if (tryPlay(1, { (getState() == EntityState.JUMP || it.name != playData[EntityState.JUMP]!!) && it.level == 1 && stopFilter.invoke(it) }) {
            it.boneBlacklist.addAll(listOf("rightItem", "rightArm", "leftItem", "leftArm"))
                it.startTransSpeed = 2f
        }) {
            modify()
        }
    }

}