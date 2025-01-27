package cn.solarmoon.spark_core.animation.preset_anim

import cn.solarmoon.spark_core.registry.common.SparkTypedAnimations
import ru.nsk.kstatemachine.state.DefaultState

sealed class EntityStates {

    object Idle : DefaultState(SparkTypedAnimations.IDLE.get().registryKey.toString())
    object Walk : DefaultState(SparkTypedAnimations.WALK.get().registryKey.toString())
    object WalkBack : DefaultState(SparkTypedAnimations.WALK_BACK.get().registryKey.toString())
    object Sprinting : DefaultState(SparkTypedAnimations.SPRINTING.get().registryKey.toString())
    object Fly : DefaultState(SparkTypedAnimations.FLY.get().registryKey.toString())
    object FlyMove : DefaultState(SparkTypedAnimations.FLY_MOVE.get().registryKey.toString())
    object Crouching : DefaultState(SparkTypedAnimations.CROUCHING.get().registryKey.toString())
    object CrouchingMove : DefaultState(SparkTypedAnimations.CROUCHING_MOVE.get().registryKey.toString())
    object Fall : DefaultState(SparkTypedAnimations.FALL.get().registryKey.toString())
    object Sit : DefaultState(SparkTypedAnimations.SIT.get().registryKey.toString())
    object FallFlying : DefaultState(SparkTypedAnimations.FALL_FLYING.get().registryKey.toString())
    object Sleeping: DefaultState(SparkTypedAnimations.SLEEPING.get().registryKey.toString())
    object Swimming: DefaultState(SparkTypedAnimations.SWIMMING.get().registryKey.toString())
    object SwimmingIdle : DefaultState(SparkTypedAnimations.SWIMMING_IDLE.get().registryKey.toString())
    
}