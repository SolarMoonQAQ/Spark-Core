package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.DefaultLayer
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
            val provider = AnimPlayDataProvider(DefaultLayer.BASE_ADDITIVE_LAYER) { AnimLayerData(enterTransitionTime = 0) }
            val mainHand = state("$name.main_hand") { payload = provider }
            val offHand = state("$name.off_hand") { payload = provider }
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
                    entity.attackAnim > 0 -> swing
                    else -> none
                }
                s1 ?: s2
            }
        }

        onStateEntry { s, b ->
            if (entity !is IEntityAnimatable<*>) return@onStateEntry
            entity.animController.getLayer(DefaultLayer.BASE_ADDITIVE_LAYER).stopAnimation()
            val lastState = b.transition.sourceState
            val sName = s.name ?: return@onStateEntry
            val animName = "state.use.$sName"
            val data = s.payload
            if (data !is AnimPlayDataProvider) return@onStateEntry
            val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.EntityUseState(entity, AnimInstance.create(entity, animName), this, data))
            if (event.isCanceled) return@onStateEntry
            val anim = (event.newAnim ?: event.originAnim)?.apply { shouldTurnBody = true } ?: return@onStateEntry
            entity.animController.getLayer(data.layerId).setAnimation(anim, data.data(lastState))
        }

    }

    suspend fun IState.initHandState(provider: AnimPlayDataProvider = AnimPlayDataProvider(DefaultLayer.BASE_ADDITIVE_LAYER)) {
        val mainHand = state("$name.main_hand") { payload = provider }
        val offHand = state("$name.off_hand") { payload = provider }
        initialChoiceState {
            val useItem = entity.useItem
            if (ItemStack.isSameItemSameComponents(useItem, entity.mainHandItem)) mainHand else offHand
        }
    }

    override fun progress() {
        machine.processEventBlocking(SwitchEvent)
    }

}