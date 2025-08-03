package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.anim.play.blend.BlendData
import ru.nsk.kstatemachine.state.IState

interface AnimPlayDataProvider

data class BlendDataProvider(
    val blendData: (IState?) -> BlendData = { BlendData() }
): AnimPlayDataProvider

data class MainPlayDataProvider(
    val transTime: (IState?) -> Int = { 7 }
): AnimPlayDataProvider