package cn.solarmoon.spark_core.util

import net.minecraft.client.player.Input
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

enum class MoveDirection {
    FORWARD, BACKWARD, LEFT, RIGHT;

    val simpleName get() = toString().lowercase()

    companion object {
        /**
         * 通过客户端输入获取，没有输入返回null
         */
        @OnlyIn(Dist.CLIENT)
        @JvmStatic
        fun getByInput(input: Input): MoveDirection? {
            return when {
                input.forwardImpulse < 0 -> BACKWARD
                input.left -> LEFT
                input.right -> RIGHT
                input.hasForwardImpulse() -> FORWARD
                else -> null
            }
        }
    }

}