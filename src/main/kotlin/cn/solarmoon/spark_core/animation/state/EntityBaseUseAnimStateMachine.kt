package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendData
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import net.minecraft.core.registries.BuiltInRegistries
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
) {

    object SwitchEvent: Event

    val useMachine = createStdLibStateMachine {
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

        transitionOn<SwitchEvent> {
            targetState = {
                val useAnim = entity.useItem.useAnimation
                when(useAnim) {
                    UseAnim.EAT -> eat
                    UseAnim.DRINK -> drink
                    UseAnim.BOW -> bow
                    UseAnim.BLOCK -> block
                    UseAnim.BRUSH -> brush
                    UseAnim.CROSSBOW -> crossbow
                    UseAnim.SPEAR -> spear
                    UseAnim.TOOT_HORN -> tootHorn
                    UseAnim.SPYGLASS -> spyglass
                    else -> none
                }
            }
        }

        var lastBlendAnimId: String? = null

        onStateEntry { s, b ->
            if (entity !is IEntityAnimatable<*>) return@onStateEntry
            lastBlendAnimId?.let { id -> entity.animController.removeBlend(id) }
            val entityLocation = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
            val lastState = b.transition.sourceState
            val sName = s.name ?: return@onStateEntry
            val animName = "state.use.$sName"
            val data = s.payload
            if (data !is AnimPlayDataProvider) return@onStateEntry
            SparkCore.LOGGER.info(animName)
            // 使用SparkResourcePathBuilder构建四层格式的动画路径
            val basePath = SparkResourcePathBuilder.buildAnimationPathFromEntityAuto(entity.type)
            val animationPath = SparkResourcePathBuilder.buildAnimationPath(
                basePath.namespace,
                basePath.path.split("/")[0], // moduleName
                basePath.path.split("/").drop(2).joinToString("/"), // entityPath
                animName
            )
            SparkRegistries.TYPED_ANIMATION.get(animationPath)?.let {
                val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.EntityUseState(entity, it, this, data))
                if (event.isCanceled) return@let
                val anim = event.newAnim ?: event.originAnim
                if (data is BlendDataProvider) {
                    val bd = data.blendData(lastState)
                    anim.blend(entity, bd)
                    lastBlendAnimId = anim.index.locationName
                } else if (data is MainPlayDataProvider) {
                    val tt = data.transTime(lastState)
                    anim.play(entity, tt)
                }
            }
        }

    }

    suspend fun IState.initHandState() {
        val mainHand = state("$name.main_hand") { payload = BlendDataProvider { BlendData(10000.0) } }
        val offHand = state("$name.off_hand") { payload = BlendDataProvider { BlendData(10000.0) } }
        initialChoiceState {
            val useItem = entity.useItem
            if (ItemStack.isSameItemSameComponents(useItem, entity.mainHandItem)) mainHand else offHand
        }
    }

    fun progress() {
        useMachine.processEventBlocking(SwitchEvent)
    }

}