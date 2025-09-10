package cn.solarmoon.spark_core.client.gui.screen

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

//class JSScriptBrowserScreen : Screen(Component.translatable("spark_core.screen.js_browser.title")) {
//
//    private lateinit var scriptList: JSScriptList
//    private lateinit var searchBox: EditBox
//    private lateinit var refreshButton: Button
//    private var selectedScript: ResourceLocation? = null
//    private var scriptContent: String = ""
//
//    // 滚动相关
//    private var scrollOffset = 0
//    private var maxScrollOffset = 0
//
//    companion object {
//        // 静态引用当前打开的界面实例，用于自动刷新
//        @Volatile
//        private var currentInstance: JSScriptBrowserScreen? = null
//
//        @JvmStatic
//        fun refreshCurrentInstance() {
//            currentInstance?.refreshScripts()
//            SparkCore.LOGGER.debug("自动刷新JS脚本浏览器界面")
//        }
//    }
//
//    override fun init() {
//        super.init()
//
//        // 设置当前实例引用
//        currentInstance = this
//
//        // 搜索框
//        searchBox = EditBox(font, 20, 40, width / 3 - 40, 20, Component.literal("Search scripts..."))
//        addRenderableWidget(searchBox)
//
//        // 刷新按钮
//        refreshButton = Button.builder(Component.literal("Refresh")) { refreshScripts() }
//            .bounds(width / 3 - 80, 10, 60, 20)
//            .build()
//        addRenderableWidget(refreshButton)
//
//        // 脚本列表 - 左侧1/3宽度，从Y=70开始到底部-30的高度
//        val listWidth = width / 3
//        val listTop = 70
//        val listBottom = height - 30
//        scriptList = JSScriptList(minecraft!!, listWidth, listBottom - listTop, listTop, 20)
//        addRenderableWidget(scriptList)
//
//        refreshScripts()
//    }
//
//    override fun removed() {
//        super.removed()
//        // 清除当前实例引用
//        currentInstance = null
//    }
//
//    private fun refreshScripts() {
//        scriptList.refreshScripts()
//    }
//
//    private fun selectScript(location: ResourceLocation) {
//        selectedScript = location
//        scriptContent = loadScriptContent(location)
//    }
//
//    private fun loadScriptContent(location: ResourceLocation): String {
//        return try {
//            val script = SparkRegistries.JS_SCRIPTS?.get(location)
//            script?.content ?: "// 脚本内容加载失败"
//        } catch (e: Exception) {
//            "// 错误: ${e.message}"
//        }
//    }
//
//    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
//        // 渲染半透明背景而不是默认的模糊背景
//        guiGraphics.fill(0, 0, width, height, 0x88000000.toInt())
//    }
//
//    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
//        // 渲染自定义背景
//        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
//
//        // 渲染所有注册的小部件（包括搜索框和刷新按钮）
//        super.render(guiGraphics, mouseX, mouseY, partialTick)
//
//        // 渲染脚本列表
//        scriptList.render(guiGraphics, mouseX, mouseY, partialTick)
//
//        // 渲染标题
//        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
//
//        // 渲染脚本查看器
//        renderScriptViewer(guiGraphics)
//    }
//
//    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
//        // 检查鼠标是否在脚本查看区域
//        val viewerX = width / 3 + 10
//        val viewerY = 70
//        val viewerWidth = width - viewerX - 20
//        val viewerHeight = height - viewerY - 20
//
//        if (mouseX >= viewerX && mouseX <= viewerX + viewerWidth &&
//            mouseY >= viewerY && mouseY <= viewerY + viewerHeight) {
//
//            val scrollAmount = (scrollY * 30).toInt() // 每次滚动30像素
//            scrollOffset = (scrollOffset - scrollAmount).coerceIn(0, maxScrollOffset)
//            return true
//        }
//
//        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
//    }
//
//    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
//        // 检查调试按钮点击
//        if (selectedScript != null && button == 0) {
//            val script = SparkRegistries.JS_SCRIPTS?.get(selectedScript!!)
//            if (script?.apiId == "skill") {
//                val viewerX = width / 3 + 10
//                val viewerY = 70
//                val viewerWidth = width - viewerX - 20
//
//                val debugButtonX = viewerX + viewerWidth - 80
//                val debugButtonY = viewerY + 5
//                val debugButtonWidth = 70
//                val debugButtonHeight = 16
//
//                if (mouseX >= debugButtonX && mouseX <= debugButtonX + debugButtonWidth &&
//                    mouseY >= debugButtonY && mouseY <= debugButtonY + debugButtonHeight) {
//
//                    triggerSkillDebug(selectedScript!!)
//                    return true
//                }
//            }
//        }
//
//        return super.mouseClicked(mouseX, mouseY, button)
//    }
//
//    private fun triggerSkillDebug(scriptLocation: ResourceLocation) {
//        try {
//            val player = minecraft?.player
//            if (player != null) {
//                // 使用现有的spark命令系统：spark skill play @s <skill_id>
//                val skillId = buildString {
//                    append(scriptLocation.namespace)
//                    append(":")
//                    append(
//                        scriptLocation.path.removePrefix("script/")
//                            .removePrefix("skill/")
//                            .removeSuffix(".js")
//                    )
//                }
//                player.connection.sendCommand("spark skill play @s $skillId")
//                SparkCore.LOGGER.info("通过调试按钮触发技能: $skillId")
//
//                // 显示反馈消息
//                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a调试技能: §e$skillId"))
//            }
//        } catch (e: Exception) {
//            SparkCore.LOGGER.error("触发技能调试失败: ${e.message}")
//
//            // 显示错误消息
//            minecraft?.player?.sendSystemMessage(
//                net.minecraft.network.chat.Component.literal("§c调试技能失败: ${e.message}")
//            )
//        }
//    }
//
//    private fun renderScriptViewer(guiGraphics: GuiGraphics) {
//        val x = width / 3 + 10
//        val y = 70
//        val viewWidth = width - x - 20
//        val viewHeight = height - y - 20
//
//        // 绘制背景边框
//        guiGraphics.fill(x, y, x + viewWidth, y + viewHeight, 0x88000000.toInt())
//        guiGraphics.renderOutline(x, y, viewWidth, viewHeight, 0xFFFFFFFF.toInt())
//
//        if (selectedScript != null) {
//            val script = SparkRegistries.JS_SCRIPTS?.get(selectedScript!!)
//
//            // 绘制脚本信息和调试按钮
//            guiGraphics.drawString(font, "Script: ${selectedScript!!.path}", x + 5, y + 5, 0xFFFFFF)
//            if (script != null) {
//                guiGraphics.drawString(font, "API: ${script.apiId}", x + 5, y + 16, 0xFFFF00)
//
//                // 添加调试按钮（仅对skill API显示）
//                if (script.apiId == "skill") {
//                    val debugButtonX = x + viewWidth - 80
//                    val debugButtonY = y + 5
//                    val debugButtonWidth = 70
//                    val debugButtonHeight = 16
//
//                    // 绘制调试按钮
//                    guiGraphics.fill(debugButtonX, debugButtonY, debugButtonX + debugButtonWidth, debugButtonY + debugButtonHeight, 0xFF4CAF50.toInt())
//                    guiGraphics.renderOutline(debugButtonX, debugButtonY, debugButtonWidth, debugButtonHeight, 0xFFFFFFFF.toInt())
//                    guiGraphics.drawCenteredString(font, "Debug Test", debugButtonX + debugButtonWidth / 2, debugButtonY + 4, 0xFFFFFF)
//                }
//            }
//
//            // 内容显示区域
//            val contentY = y + 35
//            val contentHeight = viewHeight - 35
//            val lineHeight = font.lineHeight + 1
//
//            // 启用剪切区域以实现滚动
//            guiGraphics.enableScissor(x, contentY, x + viewWidth - 10, contentY + contentHeight)
//
//            // 绘制脚本内容（支持滚动）
//            val lines = scriptContent.split("\n")
//            maxScrollOffset = maxOf(0, (lines.size * lineHeight) - contentHeight)
//
//            for ((index, line) in lines.withIndex()) {
//                val lineY = contentY + (index * lineHeight) - scrollOffset
//
//                // 跳过不在可见区域的行
//                if (lineY < contentY - lineHeight || lineY > contentY + contentHeight) continue
//
//                val color = when {
//                    line.trimStart().startsWith("//") -> 0x888888 // 注释
//                    line.contains("function") || line.contains("=>") -> 0x66DDFF // 函数
//                    line.contains("const ") || line.contains("let ") || line.contains("var ") -> 0x4EC9B0 // 变量
//                    line.contains("if ") || line.contains("else") || line.contains("return") -> 0xC586C0 // 关键字
//                    line.trimStart().startsWith("Skill.create") -> 0xDCDC78 // Skill API调用
//                    else -> 0xFFFFFF // 默认
//                }
//
//                guiGraphics.drawString(font, line, x + 5, lineY, color)
//            }
//
//            guiGraphics.disableScissor()
//
//            // 绘制滚动条（如果需要）
//            if (maxScrollOffset > 0) {
//                val scrollBarX = x + viewWidth - 8
//                val scrollBarY = contentY
//                val scrollBarHeight = contentHeight
//                val thumbHeight = maxOf(10, (scrollBarHeight * scrollBarHeight) / (scrollBarHeight + maxScrollOffset))
//                val thumbY = scrollBarY + (scrollOffset.toFloat() * (scrollBarHeight - thumbHeight) / maxScrollOffset).toInt()
//
//                // 滚动条背景
//                guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 6, scrollBarY + scrollBarHeight, 0x44FFFFFF)
//                // 滚动条拖拽条
//                guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbHeight.toInt(), 0xAAFFFFFF.toInt())
//            }
//        } else {
//            val hintText = "选择一个脚本来查看内容"
//            guiGraphics.drawCenteredString(font, hintText, x + viewWidth / 2, y + viewHeight / 2, 0x888888)
//        }
//    }
//
//    private inner class JSScriptList(
//        minecraft: Minecraft,
//        width: Int,
//        height: Int,
//        y: Int,
//        itemHeight: Int
//    ) : ObjectSelectionList<JSScriptList.Entry>(minecraft, width, height, y, itemHeight) {
//
//        fun refreshScripts() {
//            clearEntries()
//            SparkCore.LOGGER.debug("刷新JS脚本列表...")
//
//            SparkRegistries.JS_SCRIPTS?.entrySet()?.forEach { entry ->
//                addEntry(Entry(entry.key.location(), entry.value))
//                SparkCore.LOGGER.debug("添加脚本到列表: ${entry.key.location()}")
//            }
//
//            val totalScripts = SparkRegistries.JS_SCRIPTS?.entrySet()?.size ?: 0
//            SparkCore.LOGGER.debug("脚本列表刷新完成，共 $totalScripts 个脚本")
//        }
//
//        override fun getRowWidth(): Int = width - 12
//        override fun getScrollbarPosition(): Int = x + width - 6
//
//        inner class Entry(val location: ResourceLocation, val script: OJSScript) : ObjectSelectionList.Entry<Entry>() {
//
//            override fun render(
//                guiGraphics: GuiGraphics,
//                index: Int,
//                top: Int,
//                left: Int,
//                width: Int,
//                height: Int,
//                mouseX: Int,
//                mouseY: Int,
//                isHovering: Boolean,
//                partialTick: Float
//            ) {
//                if (this@JSScriptList.selected == this) {
//                    guiGraphics.fill(left, top, left + width, top + height, 0x66FFFFFF.toInt())
//                }
//
//                guiGraphics.drawString(font, "[${script.apiId}] ${location.path}", left + 2, top + 2, 0xFFFFFF)
//            }
//
//            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
//                if (button == 0) {
//                    this@JSScriptList.selected = this
//                    selectScript(location)
//                    return true
//                }
//                return false
//            }
//
//            override fun getNarration(): Component = Component.literal(location.path)
//        }
//    }
//}