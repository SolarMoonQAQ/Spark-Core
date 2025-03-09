package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.registry.common.SparkTypedAnimations
import ru.nsk.kstatemachine.state.DefaultState

sealed class EntityStates {

    class Idle : DefaultState(SparkTypedAnimations.IDLE.get().registryKey.toString())
    class Walk : DefaultState(SparkTypedAnimations.WALK.get().registryKey.toString())
    class WalkBack : DefaultState(SparkTypedAnimations.WALK_BACK.get().registryKey.toString())
    class Sprinting : DefaultState(SparkTypedAnimations.SPRINTING.get().registryKey.toString())
    class Fly : DefaultState(SparkTypedAnimations.FLY.get().registryKey.toString())
    class FlyMove : DefaultState(SparkTypedAnimations.FLY_MOVE.get().registryKey.toString())
    class Crouching : DefaultState(SparkTypedAnimations.CROUCHING.get().registryKey.toString())
    class CrouchingMove : DefaultState(SparkTypedAnimations.CROUCHING_MOVE.get().registryKey.toString())
    class Fall : DefaultState(SparkTypedAnimations.FALL.get().registryKey.toString())
    class Sit : DefaultState(SparkTypedAnimations.SIT.get().registryKey.toString())
    class FallFlying : DefaultState(SparkTypedAnimations.FALL_FLYING.get().registryKey.toString())
    class Sleeping: DefaultState(SparkTypedAnimations.SLEEPING.get().registryKey.toString())
    class Swimming: DefaultState(SparkTypedAnimations.SWIMMING.get().registryKey.toString())
    class SwimmingIdle : DefaultState(SparkTypedAnimations.SWIMMING_IDLE.get().registryKey.toString())
    
}