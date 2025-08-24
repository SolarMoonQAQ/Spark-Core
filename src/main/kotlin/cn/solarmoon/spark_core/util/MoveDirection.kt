package cn.solarmoon.spark_core.util

import com.mojang.serialization.Codec
import net.minecraft.client.player.Input
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

enum class MoveDirection(
    private val id: String
): StringRepresentable {
    FORWARD("forward"), BACKWARD("backward"), LEFT("left"), RIGHT("right");

    val translatableName = Component.translatable("move_direction.${serializedName}")

    override fun getSerializedName(): String {
        return id
    }

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

        val CODEC: Codec<MoveDirection> = StringRepresentable.fromEnum(::values)
    }

}