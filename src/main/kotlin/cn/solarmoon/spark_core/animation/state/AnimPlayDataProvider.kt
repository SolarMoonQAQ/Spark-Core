package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.anim.play.blend.BlendData
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendMask

interface AnimPlayDataProvider

data class BlendDataProvider(
    val blendData: () -> BlendData = { BlendData() }
): AnimPlayDataProvider

data class MainPlayDataProvider(
    val transTime: () -> Int = { 7 }
): AnimPlayDataProvider