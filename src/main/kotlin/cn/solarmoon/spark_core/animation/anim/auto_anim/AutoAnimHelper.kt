package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation

inline fun <reified T: EntityAutoAnim> IEntityAnimatable<*>.getAutoAnim(prefix: String) = autoAnims.firstOrNull { it.prefix == prefix && it is T } as? T

fun IEntityAnimatable<*>.isPlayingHitAnim(level: Int = 0, filter: (MixedAnimation) -> Boolean = { true }) = autoAnims.filter { it is HitAutoAnim }.any { it.isPlaying(level, filter) }