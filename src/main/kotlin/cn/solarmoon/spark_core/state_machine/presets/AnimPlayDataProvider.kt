package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.animation.anim.AnimGroups
import net.minecraft.resources.ResourceLocation
import ru.nsk.kstatemachine.state.IState

//data class AnimPlayDataProvider(
//    val layerId: ResourceLocation = DefaultLayer.BASE_LAYER,
//    val data: (IState?) -> AnimLayerData = { AnimLayerData() }
//)

data class AnimPayload(
    val transTime: Float = 0.15f,
    val group: Int = AnimGroups.STATE,
)