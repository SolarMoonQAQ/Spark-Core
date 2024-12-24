package cn.solarmoon.spark_core.event

import net.minecraft.client.Options
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent

abstract class KeyboardInputTickEvent(
    val options: Options,
    val isSneaking: Boolean,
    val sneakingSpeedMultiplier: Float
): Event() {

    class Pre(
        options: Options,
        isSneaking: Boolean,
        sneakingSpeedMultiplier: Float
    ): KeyboardInputTickEvent(options, isSneaking, sneakingSpeedMultiplier), ICancellableEvent

    class Post(
        options: Options,
        isSneaking: Boolean,
        sneakingSpeedMultiplier: Float
    ): KeyboardInputTickEvent(options, isSneaking, sneakingSpeedMultiplier)

}