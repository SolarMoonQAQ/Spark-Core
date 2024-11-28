package cn.solarmoon.spirit_of_fight.feature.fight_skill.skill

import cn.solarmoon.spark_core.api.animation.IEntityAnimatable
import cn.solarmoon.spark_core.api.animation.anim.play.AnimModificationData
import cn.solarmoon.spark_core.api.animation.sync.SyncedAnimation
import cn.solarmoon.spark_core.api.animation.anim.play.MixedAnimation
import cn.solarmoon.spark_core.api.phys.obb.OrientedBoundingBox
import cn.solarmoon.spark_core.api.entity.skill.AnimSkill
import cn.solarmoon.spark_core.api.entity.preinput.getPreInput
import cn.solarmoon.spark_core.api.entity.skill.IBoxBoundToBoneAnimSkill
import cn.solarmoon.spirit_of_fight.feature.fight_skill.controller.FightSkillController
import cn.solarmoon.spirit_of_fight.feature.fight_skill.spirit.getFightSpirit
import cn.solarmoon.spirit_of_fight.feature.fight_skill.sync.FightSpiritPayload
import cn.solarmoon.spirit_of_fight.feature.hit.HitType
import com.google.common.collect.HashBiMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * @param attackChangeNode 变招节点，int为可以变到这一招的连招序列号，比如想在第0招可以变到第1招，那么输入1即可。double为变招到下一个连招动画的起始位置（比如0.0就是从当前连招动画位置过渡到下一个连招动画的开始，0.5则过渡到下一个连招动画的0.5s处）
 */
abstract class ComboAnimSkill( // 在我想好用什么数据包写法表示每个点的时间碰撞箱之前先用抽象的
    private val controller: FightSkillController,
    val animGroup: Map<Int, SyncedAnimation>,
    private val attackSwitchNode: Map<Int, Double>,
    private val attackChangeNode: Map<Int, Double>,
    private val damageMultiplier: Map<Int, Float>,
    private val hitType: Map<Int, HitType>
): AnimSkill(
    controller.animatable,
    buildSet { animGroup.values.forEach { add(it.anim.name) } }
), IBoxBoundToBoneAnimSkill {

    val baseAttackSpeed = controller.baseAttackSpeed
    override val boxSize: Vector3f = controller.commonBoxSize
    override val boxOffset: Vector3f = controller.commonBoxOffset

    override fun getBoundBoneName(anim: MixedAnimation): String = "rightItem"

    companion object {
        @JvmStatic
        fun createComboConsumeAnims(prefix: String, maxCombo: Int): Map<Int, SyncedAnimation> = buildMap {
            for (i in 0 until maxCombo) {
                put(i, SyncedAnimation(MixedAnimation("$prefix:attack_$i", startTransSpeed = 5f)))
            }
        }
    }

    val preInput get() = entity.getPreInput()

    val animBiMap = HashBiMap.create(buildMap { animGroup.forEach { key, value -> put(key, value.anim.name) } })

    var index = 0

    abstract fun shouldBoxSummon(index: Int, anim: MixedAnimation): Boolean

    abstract fun getMoveByIndex(index: Int, anim: MixedAnimation): Vec3?

    override fun getBox(anim: MixedAnimation): List<OrientedBoundingBox> {
        return if (shouldBoxSummon(animBiMap.inverse()[anim.name]!!, anim)) super.getBox(anim) else listOf()
    }

    override fun getMove(anim: MixedAnimation): Vec3? {
        return getMoveByIndex(animBiMap.inverse()[anim.name]!!, anim)
    }

    override fun onBoxSummon(box: OrientedBoundingBox, anim: MixedAnimation) {
        box.extendByEntityInteractRange(entity)
        attack(box, CompoundTag().apply { putString("hit", hitType[animBiMap.inverse()[anim.name]]!!.toString()) })
    }

    fun start(change: Boolean, sync: (SyncedAnimation) -> Unit = {}) {
        // 最后一段只在结束过渡时才能预输入，同时保证了index不会超过size值
        if (index <= animGroup.size - 1) preInput.setInput("combo") {
            animGroup[index]?.let {
                it.consume(animatable, getAnimModifier(change))
                sync.invoke(it)
            }
            index++
        }
    }

    fun getAnimModifier(change: Boolean): AnimModificationData = AnimModificationData(
        getAttackAnimSpeed(baseAttackSpeed),
        if (change) 2f else if (controller.isAttacking { !it.isCancelled } && !isPlaying()) 2f else -1f, // 特殊攻击到普通攻击的过渡时间上升
        if (change) attackChangeNode[index]!!.toFloat() * 20 else -1f
    )

    override fun whenInAnim(anim: MixedAnimation) {
        super.whenInAnim(anim)

        val id = animBiMap.inverse()[anim.name]!!
        // 如果正在播放任何连击动画，按规定的结束点进行切换（预输入调用）
        val anim = anim.takeIf { !it.isCancelled } ?: return
        val switch = attackSwitchNode[id] ?: anim.maxTick
        if (preInput.hasInput() && !HitType.isPlayingHitAnim(animatable) { !it.isCancelled } ) {
            // 这一段使得连招在50-150ms之间可以变招
            val changeNode = attackChangeNode[index]
            if (preInput.id == "combo" && changeNode != null && !anim.isInTransition && anim.isTickIn(0.05, 0.15)) {
                start(true)
                preInput.invokeInput()
            } else if (anim.isTickIn(switch, anim.maxTick)) { // 这一段则是到特定时间不用等动画结束可以直接执行下一个操作
                preInput.invokeInput()
            }
        }
    }

    override fun getAttackDamageMultiplier(anim: MixedAnimation): Float? {
        val index = animBiMap.inverse()[anim.name]!!
        return damageMultiplier[index]
    }

    override fun onTargetAttacked(target: Entity) {
        getPlayingAnim()?.let { anim ->
            val mul = getAttackDamageMultiplier(anim) ?: 1f
            val fs = entity.getFightSpirit()
            fs.addStage(mul)
            fs.syncToClient(entity.id, FightSpiritPayload.Type.ADD)
        }
    }

}