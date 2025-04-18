package cn.solarmoon.spark_core.client.gui.browser

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.BufferUploader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.joml.Matrix4f

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
    width: Int,
    height: Int,
    private val initialUrl: String,
    private val transparent: Boolean = false
) : AbstractWidget(x, y, width, height, Component.empty()) { // 使用空的 Component，因为 Web 部件通常没有自己的标签

    private var browser: MCEFBrowser? = null
    private var browserTextureId: Int = 0
    private var lastMouseX: Double = 0.0
    private var lastMouseY: Double = 0.0

    init {
        attemptInit()
        if (browser == null) {
            MCEF.getLogger().warn("MCEF not initialized when creating WebBrowserWidget. Browser creation deferred.")
        }
    }

    private fun createBrowserInstance() {
        if (browser == null && MCEF.isInitialized()) {
            try {
                browser = MCEF.createBrowser(initialUrl, transparent, this.width, this.height)
                MCEF.getLogger().info("MCEF browser instance created for URL: $initialUrl")
            } catch (e: Exception) {
                MCEF.getLogger().error("Failed to create MCEF browser instance", e)
            }
        }
    }

    fun attemptInit(){
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

        RenderSystem.enableDepthTest()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, this.alpha)
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
        val u1 = 0f
        val v1 = 1f
        val u2 = 1f
        val v2 = 0f

        builder.addVertex(matrix, x1, y1, 0f).setUv(u1, v1)
        builder.addVertex(matrix, x1, y2, 0f).setUv(u1, v2)
        builder.addVertex(matrix, x2, y2, 0f).setUv(u2, v2)
        builder.addVertex(matrix, x2, y1, 0f).setUv(u2, v1)

        BufferUploader.drawWithShader(builder.buildOrThrow())

        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isMouseOver(mouseX, mouseY)) {
            this.isFocused = true
            browser?.sendMousePress(toBrowserX(mouseX), toBrowserY(mouseY), button)
            return true
        }
        this.isFocused = false
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        browser?.sendMouseRelease(toBrowserX(mouseX), toBrowserY(mouseY), button)
        return isMouseOver(mouseX, mouseY)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        browser?.sendMouseMove(toBrowserX(mouseX), toBrowserY(mouseY))
        return button == 0
    }
    
    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        browser?.sendMouseMove(toBrowserX(mouseX), toBrowserY(mouseY))
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isMouseOver(mouseX, mouseY)) {
            val deltaY = scrollY * 16.0
            browser?.sendMouseWheel(toBrowserX(mouseX), toBrowserY(mouseY), deltaY, mapModifiers(0))
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (this.isFocused) {
            browser?.sendKeyPress(keyCode, scanCode.toLong(), mapModifiers(modifiers))
            return true
        }
        return false
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
         if (this.isFocused) {
            browser?.sendKeyRelease(keyCode, scanCode.toLong(), mapModifiers(modifiers))
            return true
        }
        return false
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (this.isFocused && !Character.isISOControl(codePoint)) {
             browser?.sendKeyTyped(codePoint, mapModifiers(modifiers))
            return true
        }
        return false
    }

    private fun toBrowserX(mouseX: Double): Int {
        return (mouseX - this.x).toInt()
    }

    private fun toBrowserY(mouseY: Double): Int {
        return (mouseY - this.y).toInt()
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

    override fun setX(x: Int) {
        super.setX(x)
    }
    
    override fun setY(y: Int) {
        super.setY(y)
    }
    
    override fun setWidth(width: Int) {
        super.setWidth(width)
        browser?.resize(this.width, this.height)
    }
    
    override fun setHeight(height: Int) {
        super.setHeight(height)
        browser?.resize(this.width, this.height)
    }

    fun resize(newWidth: Int, newHeight: Int) {
        this.width = newWidth
        this.height = newHeight
        browser?.resize(newWidth, newHeight)
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