package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.client.gui.screen.AnimationDebugScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

object KeyBindings {
    val OPEN_ANIMATION_DEBUG = KeyMapping(
        "key.spark_core.open_animation_debug", // 按键描述
        GLFW.GLFW_KEY_F8,  // 默认按键代码
        "key.categories.spark_core" // 分类
    )

    @SubscribeEvent
    fun registerKeyBindings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_ANIMATION_DEBUG)
    }

    fun handleKeyInputs() {
        if (OPEN_ANIMATION_DEBUG.consumeClick()) {
            Minecraft.getInstance().setScreen(AnimationDebugScreen())
        }
    }
}