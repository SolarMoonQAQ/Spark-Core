package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.state_machine.StateMachineHandler
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.neoforged.neoforge.common.NeoForge
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.processEventBlocking

class EntityBaseUseAnimStateMachine(
    val entity: LivingEntity
): StateMachineHandler {

    override var isActive = true

    object SwitchEvent: Event

    override val machine = createStdLibStateMachine {
        val none = initialState("none")
        val eat = state("eat") { initHandState() }
        val drink = state("drink") { initHandState() }
        val bow = state("bow") { initHandState() }
        val block = state("block") { initHandState() }
        val brush = state("brush") { initHandState() }
        val crossbow = state("crossbow") { initHandState() }
        val spear = state("spear") { initHandState() }
        val tootHorn = state("toot_horn") { initHandState() }
        val spyglass = state("spyglass") { initHandState() }
        val swing = state("swing") {
            val mainHand = state("$name.main_hand") { payload = AnimPayload(0f) }
            val offHand = state("$name.off_hand") { payload = AnimPayload(0f) }
            initialChoiceState {
                if (entity.swingingArm == InteractionHand.MAIN_HAND) mainHand else offHand
            }
        }

        transitionOn<SwitchEvent> {
            targetState = {
                val useAnim = entity.useItem.useAnimation
                val s1 = when(useAnim) {
                    UseAnim.EAT -> eat
                    UseAnim.DRINK -> drink
                    UseAnim.BOW -> bow
                    UseAnim.BLOCK -> block
                    UseAnim.BRUSH -> brush
                    UseAnim.CROSSBOW -> crossbow
                    UseAnim.SPEAR -> spear
                    UseAnim.TOOT_HORN -> tootHorn
                    UseAnim.SPYGLASS -> spyglass
                    else -> null
                }
                val s2 = when {
                    entity.attackAnim > 0 -> {
                        tick = 1
                        swing
                    }
                    else -> if (tick <= 0) none else {
                        tick--
                        swing
                    }
                }
                s1 ?: s2
            }
        }

        onStateEntry { s, b ->
            val payload = payload
            if (entity !is IEntityAnimatable<*>) return@onStateEntry
            val sName = s.name ?: return@onStateEntry
            if (sName == "none") lastAnim?.exit()
            val animName = "state.use.$sName"
            val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.EntityUseState(entity, animInstance(entity, animName, false), this))
            if (event.isCanceled) return@onStateEntry
            val anim = (event.newAnim ?: event.originAnim) ?: return@onStateEntry
            anim.apply {
                if (payload is AnimPayload) {
                    group = payload.group
                    inTransitionTime = payload.transTime
                } else {
                    group = AnimGroups.DECOR
                }
                shouldTurnBody = true
                lastAnim?.exit()
                enter()
            }
            lastAnim = anim
        }

    }

    private var tick = 0

    private var lastAnim: AnimInstance? = null

    suspend fun IState.initHandState() {
        val mainHand = state("$name.main_hand")
        val offHand = state("$name.off_hand")
        initialChoiceState {
            val useItem = entity.useItem
            if (ItemStack.isSameItemSameComponents(useItem, entity.mainHandItem)) mainHand else offHand
        }
    }

    override fun progress() {
        machine.processEventBlocking(SwitchEvent)
    }

}