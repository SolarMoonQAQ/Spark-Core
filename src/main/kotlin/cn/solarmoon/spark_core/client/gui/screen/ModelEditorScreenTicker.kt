package cn.solarmoon.spark_core.client.gui.screen

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

// !! 重要 !!
// 这个对象需要注册到 Forge 的事件总线才能接收事件。
// 通常在客户端设置阶段完成，例如监听 FMLClientSetupEvent 或在客户端主类的构造函数中。
// 例如: NeoForge.EVENT_BUS.register(ModelEditorScreenTicker)

object ModelEditorScreenTicker {

    // 假设这是在 ClientTickEvent.ClientTickEndEvent 处理器中
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return // 需要玩家实例
        // 检查按键是否刚刚被按下
        if (SparkKeyMappings.OPEN_MODEL_EDITOR.consumeClick()) {
            // 检查玩家是否手持特殊物品 (假设物品为 SparkItems.MODEL_EDITOR_WAND)
            // 需要确保 SparkItems 已经定义并且 MODEL_EDITOR_WAND 是正确的物品引用
             // Get the actual Item instance from the DeferredItem
             val requiredItem = SparkRegistries.MODEL_EDITOR_WAND.get()
             if (player.mainHandItem.`is`(requiredItem) || player.offhandItem.`is`(requiredItem)) { // Check if player holds the wand

                println("Open Model Editor key pressed and condition met (holding Model Editor Wand).")

                // 获取玩家模型和纹理 (需要您提供具体实现)
                // 这部分逻辑高度依赖于您的 Mod 如何管理玩家模型
                // 暂时使用硬编码的占位符，您需要替换它们
                val playerModelLocation: ResourceLocation? = getPlayerModelLocation(player) // 您需要实现这个方法
                val playerTextureLocation: ResourceLocation? = getPlayerTextureLocation(player) // 您需要实现这个方法

                if (playerModelLocation != null && playerTextureLocation != null) {
                    // 确保模型已在 OModel.ORIGINS 中加载，如果不在，可能需要先触发加载
                    if (!OModel.ORIGINS.containsKey(playerModelLocation)) {
                        // 尝试加载模型？或者提示错误？
                        // OModel.loadModel(playerModelLocation) // 假设有这样的加载方法
                        player.sendSystemMessage(Component.literal("Error: Player model $playerModelLocation not found in cache."))
                        return
                    }

                    // 打开编辑器屏幕
                    minecraft.setScreen(ModelEditorScreen(playerModelLocation, playerTextureLocation))
                } else {
                    player.sendSystemMessage(Component.literal("Error: Could not determine player model/texture location."))
                }
            }
        }

    }
    // Make these private as they are helper methods for this object
    private fun getPlayerModelLocation(player: LocalPlayer): ResourceLocation? {
        val animatable = player as? IAnimatable<*>
        return animatable?.modelIndex?.modelPath // Use modelPath from ModelIndex
    }
    private fun getPlayerTextureLocation(player: LocalPlayer): ResourceLocation? {
         val animatable = player as? IAnimatable<*>
         return animatable?.modelIndex?.textureLocation // Use textureLocation from ModelIndex
    }

}