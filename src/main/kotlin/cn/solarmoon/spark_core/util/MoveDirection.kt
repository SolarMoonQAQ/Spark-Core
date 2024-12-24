package cn.solarmoon.spark_core.util

import net.minecraft.client.player.Input
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

enum class MoveDirection(val id: Int) {
    FORWARD(0), BACKWARD(1), LEFT(2), RIGHT(3);

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        /**
         * 通过id获取，方便网络传输
         */
        @JvmStatic
        fun getById(id: Int): MoveDirection = MoveDirection.entries.first { it.id == id }

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