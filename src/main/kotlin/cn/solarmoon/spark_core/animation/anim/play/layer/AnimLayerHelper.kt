package cn.solarmoon.spark_core.animation.anim.play.layer

fun AnimController.getBaseLayer() = getLayer(DefaultLayer.BASE_LAYER)

fun AnimController.getBaseAdditiveLayer() = getLayer(DefaultLayer.BASE_ADDITIVE_LAYER_2)

fun AnimController.getMainLayer() = getLayer(DefaultLayer.MAIN_LAYER)

fun AnimController.getMainAdditiveLayer() = getLayer(DefaultLayer.MAIN_ADDITIVE_LAYER)