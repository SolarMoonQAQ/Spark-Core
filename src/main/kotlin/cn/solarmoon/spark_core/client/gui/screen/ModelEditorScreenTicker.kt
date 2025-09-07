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

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return // 需要玩家实例
        
        // 检查JS脚本浏览器按键
        if (SparkKeyMappings.OPEN_JS_SCRIPT_BROWSER.get().consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(JSScriptBrowserScreen())
                SparkCore.LOGGER.info("打开JS脚本浏览器")
            }
        }
        
        // 检查模型编辑器按键
        if (SparkKeyMappings.OPEN_MODEL_EDITOR.get().consumeClick()) {
            // 检查玩家是否手持特殊物品
             val requiredItem = SparkRegistries.MODEL_EDITOR_WAND.get()
             if (player.mainHandItem.`is`(requiredItem) || player.offhandItem.`is`(requiredItem)) { // Check if player holds the wand

                println("Open Model Editor key pressed and condition met (holding Model Editor Wand).")

                val playerModelLocation: ResourceLocation? = getPlayerModelLocation(player)
                val playerTextureLocation: ResourceLocation? = getPlayerTextureLocation(player)

                if (playerModelLocation != null && playerTextureLocation != null) {
                    if (!OModel.ORIGINS.containsKey(playerModelLocation)) {
                        // 尝试加载模型？或者提示错误？
                        // OModel.loadModel(playerModelLocation)
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
        return animatable?.modelController?.model?.index?.location // Use modelPath from ModelIndex
    }
    private fun getPlayerTextureLocation(player: LocalPlayer): ResourceLocation? {
         val animatable = player as? IAnimatable<*>
         return animatable?.modelController?.textureLocation // Use textureLocation from ModelIndex
    }

}