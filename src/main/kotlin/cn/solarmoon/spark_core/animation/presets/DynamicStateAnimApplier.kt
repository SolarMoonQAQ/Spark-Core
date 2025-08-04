package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import net.neoforged.bus.api.SubscribeEvent

/**
 * DynamicStateAnimApplier listens for entity state changes and applies registered animation overrides.
 * This allows for dynamic replacement of default animations based on per-entity configurations managed
 * by AnimStateMachineManager.
 */
object DynamicStateAnimApplier {

    @SubscribeEvent
    fun onEntityStateAnimationChange(event: ChangePresetAnimEvent.EntityUseState) {
//        val entity = event.entity // event.entity is LivingEntity
//        val state = event.state // This is an instance of a class like EntityStates.Idle, EntityStates.Walk, etc.
//
//        // state.name is the key (String?) for the default animation for this state.
//        // We use this key to check if there's an override registered for this entity and state.
//        val animationStateKey = state.name
//
//        if (animationStateKey != null) {
//            // entity.id is available on LivingEntity (which extends Entity)
//            val overrideAnimation = AnimStateMachineManager.getEntityAnimationOverride(entity.stringUUID, animationStateKey)
//
//            if (overrideAnimation != null) {
//                // An override TypedAnimation was found and is valid (checked within getEntityAnimationOverride).
//                // Set it as the new animation to be played.
//                event.newAnim = overrideAnimation
//            }
//            // If no override is found, or if the found override was invalid and removed,
//            // the event proceeds with its original newAnim (usually the default animation for the state).
//        }
    }
}