package cn.solarmoon.spark_core.client.gui.browser

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一个使用 MCEF 在 Minecraft GUI 中显示网页内容的组件。
 *
 * @param x          组件的 x 坐标
 * @param y          组件的 y 坐标
 * @param width      组件的宽度
 * @param height     组件的高度
 * @param initialUrl 初始加载的 URL
 * @param transparent 背景是否透明
 */
class WebBrowserWidget(
    x: Int,
    y: Int,
    width: Int, // Widget screen width
    height: Int, // Widget screen height
    private val initialUrl: String,
    private val transparent: Boolean = false
) : AbstractWidget(x, y, width, height, Component.empty()) {

    private var browser: MCEFBrowser? = null
    private var browserTextureId: Int = 0
    private var lastMouseX: Double = 0.0
    private var lastMouseY: Double = 0.0
    private val minecraft: Minecraft = Minecraft.getInstance() // 获取 Minecraft 实例以访问键盘状态
    private var currentZoomFactor: Double = 1.0 // Current zoom factor
    private var isRecreatingBrowser: Boolean = false // Flag to prevent rapid recreation

    // Store current browser pixel dimensions (calculated dynamically)
    private var currentBrowserPixelWidth: Int = 1 // Initial default
    private var currentBrowserPixelHeight: Int = 1 // Initial default

    init {
        // Calculate initial resolution based on widget size and scale
        calculateAndSetInitialResolution()
        attemptInit()
        if (browser == null) {
            MCEF.getLogger().warn("MCEF not initialized when creating WebBrowserWidget. Browser creation deferred.")
        }
    }

    // Helper to calculate initial resolution
    private fun calculateAndSetInitialResolution() {
        val guiScale = minecraft.window.guiScale
        // Start with resolution matching scaled widget size
        this.currentBrowserPixelWidth = (this.width * guiScale * currentZoomFactor).roundToInt().coerceAtLeast(1)
        this.currentBrowserPixelHeight = (this.height * guiScale * currentZoomFactor).roundToInt().coerceAtLeast(1)
    }

    private fun createBrowserInstance() {
        if (browser == null && MCEF.isInitialized() && !isRecreatingBrowser) {
            isRecreatingBrowser = true
            // Calculate browser resolution based on widget size, gui scale, and zoom factor
            val guiScale = minecraft.window.guiScale
            currentBrowserPixelWidth = (this.width * guiScale * currentZoomFactor).roundToInt().coerceAtLeast(1)
            currentBrowserPixelHeight = (this.height * guiScale * currentZoomFactor).roundToInt().coerceAtLeast(1)

            MCEF.getLogger().info("Creating browser instance: URL=$initialUrl, WidgetSize=${this.width}x${this.height}, GuiScale=$guiScale, Zoom=$currentZoomFactor, TargetRes=${currentBrowserPixelWidth}x${currentBrowserPixelHeight}")
            try {
                browser = MCEF.createBrowser(initialUrl, transparent, currentBrowserPixelWidth, currentBrowserPixelHeight)
                MCEF.getLogger().info("MCEF browser instance created.")
            } catch (e: Exception) {
                MCEF.getLogger().error("Failed to create MCEF browser instance", e)
            } finally {
                 isRecreatingBrowser = false
            }
        }
    }

    fun attemptInit() {
        if (browser == null) {
            createBrowserInstance()
        }
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (browser == null) {
            attemptInit()
            if (browser == null) {
                return
            }
        }

        RenderSystem.assertOnRenderThread()

        val currentBrowser = browser ?: return

        val textureId = currentBrowser.renderer?.textureID ?: 0
        if (textureId != 0) {
            this.browserTextureId = textureId
        } else {
            return
        }

        RenderSystem.disableDepthTest()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.enableBlend()

        if (transparent) {
            RenderSystem.defaultBlendFunc()
        } else {
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            )
        }

        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderTexture(0, this.browserTextureId)

        val tesselator = Tesselator.getInstance()
        val builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        val matrix = guiGraphics.pose().last().pose()

        val x1 = this.x.toFloat()
        val y1 = this.y.toFloat()
        val x2 = (this.x + this.width).toFloat()
        val y2 = (this.y + this.height).toFloat()

        // --- 修正 UV 坐标以解决倒置问题 ---
        val u1 = 0f
        val v1 = 0f // V 从 0 开始 (顶部)
        val u2 = 1f
        val v2 = 1f // V 到 1 结束 (底部)

        builder.addVertex(matrix, x1, y1, 0f).setUv(u1, v1) // 左上角 (UV: 0, 0)
        builder.addVertex(matrix, x1, y2, 0f).setUv(u1, v2) // 左下角 (UV: 0, 1)
        builder.addVertex(matrix, x2, y2, 0f).setUv(u2, v2) // 右下角 (UV: 1, 1)
        builder.addVertex(matrix, x2, y1, 0f).setUv(u2, v1) // 右上角 (UV: 1, 0)

        BufferUploader.drawWithShader(builder.buildOrThrow())

        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    // --- Input Handling with Corrected Coordinates (using guiScale) ---

    // Helper to get scaled coordinate relative to widget top-left
    private fun getScaledX(screenX: Double): Int {
        val guiScale = minecraft.window.guiScale
        return ((screenX - this.x) * guiScale).roundToInt()
    }

    private fun getScaledY(screenY: Double): Int {
        val guiScale = minecraft.window.guiScale
        return ((screenY - this.y) * guiScale).roundToInt()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isMouseOver(mouseX, mouseY)) {
            this.isFocused = true
            // Convert screen coordinates to browser pixel coordinates using guiScale
            val browserX = getScaledX(mouseX).coerceIn(0, currentBrowserPixelWidth)
            val browserY = getScaledY(mouseY).coerceIn(0, currentBrowserPixelHeight)
            MCEF.getLogger().debug("WebBrowserWidget mouseClicked: Btn=$button, ScreenPos=($mouseX, $mouseY), BrowserPos=($browserX, $browserY)")
            browser?.sendMousePress(browserX, browserY, button)
            browser?.setFocus(true) 
            return true
        }
        this.isFocused = false
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val browserX = getScaledX(mouseX).coerceIn(0, currentBrowserPixelWidth)
        val browserY = getScaledY(mouseY).coerceIn(0, currentBrowserPixelHeight)
        browser?.sendMouseRelease(browserX, browserY, button)
        browser?.setFocus(true) 
        return isMouseOver(mouseX, mouseY)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val browserX = getScaledX(mouseX).coerceIn(0, currentBrowserPixelWidth)
        val browserY = getScaledY(mouseY).coerceIn(0, currentBrowserPixelHeight)
        browser?.sendMouseMove(browserX, browserY)
        browser?.setFocus(true) 
        return button == 0
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        val browserX = getScaledX(mouseX).coerceIn(0, currentBrowserPixelWidth)
        val browserY = getScaledY(mouseY).coerceIn(0, currentBrowserPixelHeight)
        browser?.sendMouseMove(browserX, browserY)
        browser?.setFocus(true) 
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isMouseOver(mouseX, mouseY)) {
            val glfwModifiers = getCurrentGlfwModifiers()
            val isCtrlDown = (glfwModifiers and GLFW.GLFW_MOD_CONTROL) != 0

            if (isCtrlDown && !isRecreatingBrowser) {
                // --- 处理缩放 (重建浏览器) ---
                val zoomStep = 0.2
                val zoomDirection = if (scrollY > 0) -1.0 else 1.0
                val newZoomFactor = (currentZoomFactor + zoomStep * zoomDirection).coerceIn(0.5, 3.0)
                if (abs(newZoomFactor - currentZoomFactor) > 0.01) {
                    MCEF.getLogger().debug("Zoom factor changed: $currentZoomFactor -> $newZoomFactor")
                    currentZoomFactor = newZoomFactor
                    browser?.close()
                    browser = null
                    browserTextureId = 0
                    createBrowserInstance()
                }
                return true
            } else if (!isCtrlDown) {
                // --- 处理普通滚动 ---
                val browserInstance = browser ?: return false
                val cefModifiers = mapModifiers(glfwModifiers)
                val deltaY = scrollY * 2.0
                // Scroll events also need coordinates mapped to browser resolution using guiScale
                val browserX = getScaledX(mouseX).coerceIn(0, currentBrowserPixelWidth)
                val browserY = getScaledY(mouseY).coerceIn(0, currentBrowserPixelHeight)
                MCEF.getLogger().debug("WebBrowserWidget mouseScrolled: deltaY=$deltaY, modifiers=$cefModifiers, BrowserPos=($browserX, $browserY)")
                browserInstance.sendMouseWheel(browserX, browserY, deltaY, cefModifiers)
                browserInstance.setFocus(true) 
                return true
            }
        }
        return false
    }

    // 辅助函数获取当前 GLFW 修饰键状态
    private fun getCurrentGlfwModifiers(): Int {
        var mods = 0
        val windowId = minecraft.window.window
        if (GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            mods = mods or GLFW.GLFW_MOD_SHIFT
        }
        if (GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            mods = mods or GLFW.GLFW_MOD_CONTROL
        }
        if (GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            mods = mods or GLFW.GLFW_MOD_ALT
        }
        if (GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowId, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS) {
            mods = mods or GLFW.GLFW_MOD_SUPER
        }
        return mods
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (this.isFocused) {
            val cefModifiers = mapModifiers(modifiers)
            MCEF.getLogger().debug("WebBrowserWidget keyPressed: keyCode=$keyCode, scanCode=$scanCode, cefModifiers=$cefModifiers")
            browser?.sendKeyPress(keyCode, scanCode.toLong(), cefModifiers)
            browser?.setFocus(true) 
            return true
        }
        return false
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
         if (this.isFocused) {
            val cefModifiers = mapModifiers(modifiers)
            // MCEF.getLogger().debug("WebBrowserWidget keyReleased: keyCode=$keyCode, scanCode=$scanCode, cefModifiers=$cefModifiers") // 可选日志
            browser?.sendKeyRelease(keyCode, scanCode.toLong(), cefModifiers)
            browser?.setFocus(true) 
            return true
        }
        return false
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (this.isFocused && !Character.isISOControl(codePoint)) {
            val cefModifiers = mapModifiers(modifiers)
            MCEF.getLogger().debug("WebBrowserWidget charTyped: codePoint=$codePoint, cefModifiers=$cefModifiers")
            browser?.sendKeyTyped(codePoint, cefModifiers)
            browser?.setFocus(true) 
            return true
        }
        return false
    }

    private fun mapModifiers(glfwModifiers: Int): Int {
        var cefModifiers = 0
        val EVENTFLAG_SHIFT_DOWN = 1 shl 1
        val EVENTFLAG_CONTROL_DOWN = 1 shl 2
        val EVENTFLAG_ALT_DOWN = 1 shl 3
        val EVENTFLAG_COMMAND_DOWN = 1 shl 4

        if ((glfwModifiers and GLFW.GLFW_MOD_SHIFT) != 0) cefModifiers = cefModifiers or EVENTFLAG_SHIFT_DOWN
        if ((glfwModifiers and GLFW.GLFW_MOD_CONTROL) != 0) cefModifiers = cefModifiers or EVENTFLAG_CONTROL_DOWN
        if ((glfwModifiers and GLFW.GLFW_MOD_ALT) != 0) cefModifiers = cefModifiers or EVENTFLAG_ALT_DOWN
        if ((glfwModifiers and GLFW.GLFW_MOD_SUPER) != 0) cefModifiers = cefModifiers or EVENTFLAG_COMMAND_DOWN

        return cefModifiers
    }

    // Reinstate browser recreation on widget resize
    override fun setWidth(width: Int) {
        val changed = this.width != width
        super.setWidth(width)
        if (changed && !isRecreatingBrowser) {
             MCEF.getLogger().debug("Widget width changed to $width, recreating browser.")
             // Zoom factor remains, recalculate resolution based on new width
             browser?.close()
             browser = null
             browserTextureId = 0
             createBrowserInstance() // Will use new this.width
        }
    }

    override fun setHeight(height: Int) {
        val changed = this.height != height
        super.setHeight(height)
         if (changed && !isRecreatingBrowser) {
             MCEF.getLogger().debug("Widget height changed to $height, recreating browser.")
             // Zoom factor remains, recalculate resolution based on new height
             browser?.close()
             browser = null
             browserTextureId = 0
             createBrowserInstance() // Will use new this.height
         }
    }

    fun close() {
        MCEF.getLogger().info("Closing MCEF browser for URL: ${browser?.url ?: initialUrl}")
        try {
            browser?.close()
        } catch (e: Exception) {
            MCEF.getLogger().error("Error closing MCEF browser", e)
        } finally {
             browser = null
             browserTextureId = 0
        }
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput)
    }

    override fun getMessage(): Component = Component.empty()

    override fun onRelease(mouseX: Double, mouseY: Double) { /* 释放逻辑在 mouseReleased 中处理 */ }

}