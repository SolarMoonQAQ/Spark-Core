package cn.solarmoon.spark_core.client.gui.screen

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.client.gui.widget.ModelTreeViewWidget
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings
import com.google.gson.GsonBuilder
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f as JMEVector3f
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.File
import java.util.*
import org.lwjgl.opengl.GL11
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import kotlin.math.max
import cn.solarmoon.spark_core.client.gui.browser.WebBrowserWidget
import com.cinemamod.mcef.MCEF
import org.joml.Vector4f
import java.util.UUID
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class ModelEditorScreen(private val modelLocation: ResourceLocation, private val textureLocation: ResourceLocation) : Screen(Component.translatable("spark_core.screen.model_editor.title")) {

    // --- Main Camera Control State (used by CameraMixin) ---
    var cameraDistance: Float = 5.0f
        private set
    var cameraYaw: Float = 0f
        private set
    var cameraPitch: Float = 15f
        private set

    // --- TreeView & Selection ---
    private lateinit var treeView: ModelTreeViewWidget
    private var selectedBone: OBone? = null
    private var selectedCube: OCube? = null
    private var model: OModel? = null // Still needed for TreeView and saving

    // --- Gizmo Interaction State ---
    private var draggingAxis: Axis? = null // null, X, Y, or Z
    private var dragStartX: Double = 0.0
    private var dragStartY: Double = 0.0

    // --- Undo/Redo History ---
    private val undoStack = LinkedList<EditAction>()
    private val redoStack = LinkedList<EditAction>()

    // --- Model Selection List ---
    private lateinit var modelSelectionList: ModelSelectionList
    private var originalPivotBeforeDrag: Vec3? = null // Store original pivot when starting drag

    // --- Animation Selection List --- M
    private lateinit var animationSelectionList: AnimationSelectionList
    private var currentAnimations: List<String> = emptyList() // Cache current model's animations

    // --- Web Browser Widget for Testing ---
    private lateinit var webBrowserWidget: WebBrowserWidget // 添加属性

    // --- Debug Ray Visualization ---
    private var debugRayOrigin: Vector3f? = null
    private var debugRayDirection: Vector3f? = null
    private var debugRayLength: Float = 100f

    private enum class Axis { X, Y, Z }

    // --- Model Selection List Widget (Inner class remains the same) ---
    private inner class ModelSelectionList(minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int)
        : ObjectSelectionList<ModelSelectionList.Entry>(minecraft, width, height, y, itemHeight) {

        init {
            // Populate the list
            OModel.ORIGINS.keys.sortedBy { it.toString() }.forEach { location ->
                this.addEntry(Entry(location))
            }
        }

        override fun getRowWidth(): Int = this.width - (if (this.maxScroll > 0) 18 else 12) // Adjust width for scrollbar

        override fun getScrollbarPosition(): Int = this.getX() + this.width - 6

        inner class Entry(val location: ResourceLocation) : ObjectSelectionList.Entry<Entry>() {
            private var lastClickTime: Long = 0
            private var lastClickLocation: ResourceLocation? = null

            override fun render(guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, isHovering: Boolean, partialTick: Float) {
                // Display the path part of the resource location
                val displayText = location.path
                val font = this@ModelEditorScreen.font
                guiGraphics.drawString(font, displayText, left + 2, top + (height - font.lineHeight) / 2, 0xFFFFFF)
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (button == 0) { // Left click
                    this@ModelSelectionList.selected = this
                    // Double click detection
                    val now = Util.getMillis()
                    if (now - lastClickTime < 250 && lastClickLocation == this.location) { // 250ms threshold for double click
                        this@ModelEditorScreen.switchModel(this.location)
                        return true
                    }
                    lastClickTime = now
                    lastClickLocation = this.location
                    return true
                }
                return false
            }

            // Required override for narration, can be basic for now
            override fun getNarration(): Component = Component.literal(location.toString())
        }
    }

    // --- Animation Selection List Widget --- M
    private inner class AnimationSelectionList(minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int)
        : ObjectSelectionList<AnimationSelectionList.Entry>(minecraft, width, height, y, itemHeight) {

        fun updateAnimations(animationNames: List<String>) {
            this.clearEntries() // Clear previous animations
            animationNames.sorted().forEach { animName ->
                this.addEntry(Entry(animName))
            }
            // Optionally reset scroll position
            this.scrollAmount = 0.0
        }

        override fun getRowWidth(): Int = this.width - (if (this.maxScroll > 0) 18 else 12)

        override fun getScrollbarPosition(): Int = this.getX() + this.width - 6

        inner class Entry(val animationName: String) : ObjectSelectionList.Entry<Entry>() {
            override fun render(guiGraphics: GuiGraphics, index: Int, top: Int, left: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, isHovering: Boolean, partialTick: Float) {
                val font = this@ModelEditorScreen.font
                guiGraphics.drawString(font, animationName, left + 2, top + (height - font.lineHeight) / 2, 0xFFFFFF)
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (button == 0) { // Left click
                    this@AnimationSelectionList.selected = this
                    this@ModelEditorScreen.playAnimation(this.animationName)
                    return true
                }
                return false
            }

            override fun getNarration(): Component = Component.literal(animationName)
        }

        // Public method to reset scroll, similar to TreeView M
        fun resetScroll() {
            this.scrollAmount = 0.0
        }
    }

    override fun init() {
        super.init()
        val minecraft = Minecraft.getInstance()
        // No level needed directly? Player exists.
        // val level = minecraft.level
        // if (level == null) { ... }

        // --- 1. Get Player as IAnimatable ---
        val player = minecraft.player
        if (player == null) {
             minecraft.gui.chat.addMessage(Component.literal("Error: Player not available!"))
            this.onClose()
            return
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        val animatable = player as? IAnimatable<*> // Assume player implements IAnimatable
        if (animatable == null) {
            minecraft.gui.chat.addMessage(Component.literal("Error: Player is not IAnimatable!"))
            this.onClose()
            return
        }

        // --- 2. (Optional) Set Initial Model on Player ---
        // This might be desired so the editor starts with the correct model shown
        try {
            animatable.modelIndex = ModelIndex(modelLocation, textureLocation)
        } catch (e: Exception) {
            minecraft.gui.chat.addMessage(Component.literal("Error: Failed to set initial ModelIndex on player: ${e.message}"))
            // Don't necessarily close, maybe it's recoverable or just visual glitch
        }


        // --- 3. Load OModel (for TreeView) ---
        model = OModel.ORIGINS[modelLocation]
        if (model == null) {
            minecraft.gui.chat.addMessage(Component.literal("Error: Model not found in ORIGINS: $modelLocation"))
            this.onClose()
            return
        }

        // --- 4. Initialize TreeView (using OModel) ---
        // Layout: Place TreeView and Model List on the right side
        val sideMargin = 10
        val topMargin = 16
        val bottomMargin = 30 // Space for buttons

        val padding = 4 // Increased padding between elements
        val availableHeight = this.height - topMargin - bottomMargin

        val sidebarWidth = 100

        val modelListY =topMargin + padding + availableHeight/2
        val modelListHeight = (availableHeight-padding)/2

        val animListY = topMargin
        val animListHeight = (availableHeight - padding)/2 // Let animation list take full height on left


        // --- Add Undo/Redo/Save Buttons ---
        val buttonY = this.height - 25 // Position buttons at the bottom left
        val buttonWidth = 50
        val buttonHeight = 20
        var currentButtonX = 10 // Start from left

        // Undo Button
        addRenderableWidget(Button.builder(Component.literal("Undo")) { _ ->
            if (undoStack.isNotEmpty()) {
                val action = undoStack.pop()
                action.undo(treeView)
                redoStack.push(action)
                println("Undo performed. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
            }
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // Redo Button
        addRenderableWidget(Button.builder(Component.literal("Redo")) { _ ->
            if (redoStack.isNotEmpty()) {
                val action = redoStack.pop()
                action.redo(treeView)
                undoStack.push(action)
                println("Redo performed. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
            }
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // Save Button
        addRenderableWidget(Button.builder(Component.literal("Save")) { _ ->
            saveModelToFile()
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())

        // --- 8. Initialize Model Selection List ---
        // Placed below TreeView on the right sidebaranimListHeight
        modelSelectionList = ModelSelectionList(minecraft, sidebarWidth, modelListHeight, modelListY, font.lineHeight + 3)
        this.addRenderableWidget(modelSelectionList)

        // --- 8.5 Initialize Animation Selection List --- M
        // Place Animation List on the left side
        animationSelectionList = AnimationSelectionList(minecraft, sidebarWidth, animListHeight, animListY, font.lineHeight + 3)
        this.addRenderableWidget(animationSelectionList)


        val sidebarX = this.width - sidebarWidth - sideMargin // From backup
        val treeViewY = topMargin // From backup
        val treeViewHeightPercent = 1f // From backup
        val treeViewHeight = (availableHeight * treeViewHeightPercent).toInt() // From backup
        treeView = ModelTreeViewWidget(sidebarX, treeViewY, sidebarWidth, treeViewHeight, model) { selectedElement ->
            // Update selection state (same logic)
            selectedBone = null
            selectedCube = null
            when (selectedElement) {
                is OBone -> selectedBone = selectedElement
                is OCube -> {
                    selectedCube = selectedElement
                    selectedBone = model?.bones?.values?.find { it.cubes.contains(selectedElement) }
                }
            }
            // Reset Gizmo drag state on new selection
            draggingAxis = null
            originalPivotBeforeDrag = null
//            println("Selected: ${selectedBone?.name ?: selectedCube?.let { "Cube in " + (model?.bones?.values?.find { b -> b.cubes.contains(it) }?.name ?: "Unknown") } ?: "None"}")
        }

        // Set root nodes (same logic)
        val rootNodes = model!!.bones.values
            .filter { it.parentName == null || model!!.bones[it.parentName] == null }
        treeView.setNodes(rootNodes)
        this.addRenderableWidget(treeView)

        // Populate initial animations and try auto-play
        // --- Fetch Initial Animation Set --- M
        val initialAnimSetKey = getAnimationSetKey(modelLocation)
        val initialAnimSet = OAnimationSet.ORIGINS[initialAnimSetKey] ?: OAnimationSet.EMPTY
        updateAndPlayIdleAnimation(animatable, initialAnimSet) // Pass the specific set

        // --- 9. Initialize Camera State ---
        // Initial camera state is now set in the properties directly
        // Reset yaw/pitch based on player potentially? Or keep fixed start?
        // Let's keep the fixed start for now. Player look dir might be distracting.
        // cameraYaw = player.yRot // Example: Start facing same way as player
        // cameraPitch = player.xRot

        // --- Add Web Browser Widget ---
        if (MCEF.isInitialized()) {
            val browserWidgetHeight = 70
            val browserWidgetWidth = this.width - sidebarWidth*2
            val browserWidgetX = sidebarWidth
            val browserWidgetY = buttonY - browserWidgetHeight - 5

            webBrowserWidget = WebBrowserWidget(
                browserWidgetX, browserWidgetY,
                browserWidgetWidth, browserWidgetHeight,
                "https://www.google.com",
                transparent = true
            )
            this.addRenderableWidget(webBrowserWidget) // Add to screen
            println("WebBrowserWidget added to ModelEditorScreen.") // Updated log message
        } else {
            println("MCEF not initialized, skipping WebBrowserWidget.")
            minecraft.gui.chat.addMessage(Component.literal("Warning: MCEF not initialized, web browser widget disabled."))
        }

        // --- Register Event Listener --- M
        NeoForge.EVENT_BUS.register(this)
    }

    override fun onClose() {
        // --- Unregister Event Listener --- M
        NeoForge.EVENT_BUS.unregister(this)
        // sceneRenderTarget?.destroyBuffers() // REMOVED
        // targetEntity?.discard() // REMOVED
        super.onClose()
        // Optional: Reset player camera if needed? Mixin should stop applying automatically.

        // --- Restore Wand --- M
        val player = Minecraft.getInstance().player
        if (player != null) {
            val wandStack = ItemStack(SparkRegistries.MODEL_EDITOR_WAND.get())
            player.setItemInHand(InteractionHand.MAIN_HAND, wandStack)
            // Optional: Send feedback?
        }

        // --- Close Browser Widget ---
        webBrowserWidget.close() // 关闭浏览器释放资源
        println("WebBrowserWidget closed.")
    }

    // Override renderBackground to do nothing, preventing default dimming/gradient
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Do nothing here to keep the background fully transparent
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // --- 1. Background Rendering is now handled (or rather, skipped) by the overridden renderBackground above ---
        // We don't call renderBackground here anymore.

        // --- 2. Render Widgets (TreeView, Buttons, List) ---
        // super.render still needs to be called to render widgets added via addRenderableWidget
        // It *shouldn't* call renderBackground itself if we override it.
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        // --- 4. Draw Overlay Text ---
         guiGraphics.drawString(this.font, "Editing: ${modelLocation.path}", 10, 5, 0xFFFFFF)

    }

    // --- Helper to draw screen lines (May still be useful if Gizmo draws on UI layer) ---
    private fun drawScreenLine(matrix: Matrix4f, normalMatrix: Matrix3f, buffer: VertexConsumer, start: Vector3f, end: Vector3f, r: Float, g: Float, b: Float, a: Float) {
        // ... (implementation remains the same for now)
        val normal = Vector3f(0f, 0f, 1f) // Simple Z normal for UI lines
        buffer.addVertex(matrix, start.x, start.y, start.z).setColor(r, g, b, a).setNormal(normal.x, normal.y, normal.z)
        buffer.addVertex(matrix, end.x, end.y, end.z).setColor(r, g, b, a).setNormal(normal.x, normal.y, normal.z)
    }


    // --- Camera Control ---
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        // Check if dragging TreeView first
         if (treeView.isMouseOver(mouseX, mouseY)) {
             if (treeView.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    return true
            }
            if (treeView.isMouseOver(dragStartX, dragStartY)){
                return true
            }
        }
         // Check if dragging ModelSelectionList
         if (modelSelectionList.isMouseOver(mouseX, mouseY)) {
             if (modelSelectionList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                  return true
             }
             if (modelSelectionList.isMouseOver(dragStartX, dragStartY)){
                 return true
             }
         }
         // Check if dragging AnimationSelectionList
        if (animationSelectionList.isMouseOver(mouseX, mouseY)) {
             if (animationSelectionList.mouseDragged(mouseX, mouseY, button, dragX, dragY)){
                 return true
             }
             if (animationSelectionList.isMouseOver(dragStartX, dragStartY)){
                 return true
             }
        }
        if (webBrowserWidget.isMouseOver(mouseX, mouseY)) {
            if (webBrowserWidget.isMouseOver(dragStartX, dragStartY)){
                return true
            }
        }
        // If not dragging UI elements, control camera or Gizmo
        if (button == 0) { // Left button drag
            // If dragging Gizmo axis (needs update)
            if (draggingAxis != null) {
                // handleGizmoDrag(mouseX, mouseY, dragX, dragY) // COMMENTED OUT - Needs rework
                return true // Consume event even if not implemented yet
            } else { // Rotate camera
                // Update camera state variables
                cameraYaw -= dragX.toFloat() * 0.5f
                cameraPitch += dragY.toFloat() * 0.5f
                cameraPitch = cameraPitch.coerceIn(-89.9f, 89.9f) // Limit pitch
                // No need to call updateSceneCameraPosition, Mixin reads state directly
                return true
            }
        }
        // TODO: Add panning (e.g., right-click drag)?

        // Fallback to super if not handled
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    // --- Gizmo Drag Handling (NEEDS REWORK) ---
    // private fun handleGizmoDrag(mouseX: Double, mouseY: Double, dragX: Double, dragY: Double) { ... } // COMMENTED OUT

    // --- Get Element Matrix (NEEDS REWORK - Use Player) ---
    // private fun getElementWorldMatrix(partialTick: Float): Matrix4f? { ... } // COMMENTED OUT

    // --- Apply Displacement (NEEDS REWORK - Use Player, Recalculate Logic) ---
    // private fun applyWorldDisplacement(worldDisplacement: Vector3f) { ... } // COMMENTED OUT

    // --- Get Parent Matrix (NEEDS REWORK - Use Player) ---
    // private fun getParentWorldMatrix(boneName: String, animatable: IAnimatable<*>, partialTick: Float): Matrix4f? { ... } // COMMENTED OUT

    // --- Model Switching Logic ---
    private fun switchModel(newModelLocation: ResourceLocation) {
        println("Switching model to: $newModelLocation")
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return // Need player

        // 1. Get the new OModel from cache
        val newOModel = OModel.ORIGINS[newModelLocation]
        if (newOModel == null) {
            minecraft.gui.chat.addMessage(Component.literal("Error: Could not find model $newModelLocation in ORIGINS cache."))
            return
        }

        // 2. Infer textureLocation (same logic)
        val newTextureLocation = try {
            // ... (texture inference logic remains the same)
            val modelPath = newModelLocation.path
            val texturePath = when {
                modelPath.startsWith("geo/model/") -> modelPath.replaceFirst("geo/model/", "textures/entity/")
                else -> "textures/entity/$modelPath" // Assume root model path maps to textures/entity/
            }
            val texturePathPng = if (texturePath.endsWith(".png")) texturePath else "$texturePath.png"
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, texturePathPng)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("Failed to infer texture location for model $newModelLocation", e)
            this.textureLocation // Fallback to original texture
        }
        println("Inferred texture location: $newTextureLocation")

        // 3. Update internal model reference (for TreeView, Save, EditAction)
        this.model = newOModel
        // Don't update this.modelLocation/textureLocation (constructor vals)

        // 4. Update *player's* model index
        val animatable = player as? IAnimatable<*>
        if (animatable != null) {
            try {
                // Set the model index on the player entity
                animatable.modelIndex = ModelIndex(newModelLocation, newTextureLocation)
            } catch (e: Exception) {
                minecraft.gui.chat.addMessage(Component.literal("Error setting model index on player: ${e.message}"))
            }
        } else {
            minecraft.gui.chat.addMessage(Component.literal("Error: Player is not IAnimatable."))
        }

        // 5. Update TreeView (same logic)
        val rootNodes = model!!.bones.values
            .filter { it.parentName == null || model!!.bones[it.parentName] == null }
        treeView.setNodes(rootNodes)

        // 6. Clear Undo/Redo stacks (same logic)
        undoStack.clear()
        redoStack.clear()

        // 7. Reset selection (same logic)
        selectedBone = null
        selectedCube = null
        draggingAxis = null
        originalPivotBeforeDrag = null

        // 8. Reset camera? (Optional)
        // cameraDistance = 5.0f
        // cameraPitch = 15.0f
        // cameraYaw = 0.0f

        // 9. Update animations list and auto-play idle M
        // --- Fetch Correct Animation Set on Switch --- M
        if (animatable != null) {
            val newAnimSetKey = getAnimationSetKey(newModelLocation)
            val newAnimSet = OAnimationSet.ORIGINS[newAnimSetKey] ?: OAnimationSet.EMPTY
             updateAndPlayIdleAnimation(animatable, newAnimSet) // Pass the specific set
        }
        println("Model switched successfully.")
    }

    // --- Restore mouseScrolled from Backup --- M
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // 优先让 WebBrowserWidget 处理滚动
        if (webBrowserWidget.mouseScrolled(mouseX, mouseY, scrollX, scrollY) == true) {
            return true // 如果浏览器处理了滚动，事件结束
        }

        // 接着检查其他可滚动组件
        if (treeView.isMouseOver(mouseX, mouseY)) {
            if (treeView.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
        }
        if (modelSelectionList.isMouseOver(mouseX, mouseY)) {
            if (modelSelectionList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
        }
        if (animationSelectionList.isMouseOver(mouseX, mouseY)) {
            if (animationSelectionList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
        }

        // 如果其他组件都没处理滚动，则调整相机距离
        val zoomAmount = scrollY * 0.1f * cameraDistance
        cameraDistance -= zoomAmount.toFloat()
        cameraDistance = cameraDistance.coerceIn(0.5f, 50f)
        return true
    }

    // isPauseScreen remains false
    override fun isPauseScreen(): Boolean = false

    // Convert screen coordinates to a ray in world space using Minecraft's pick method
    private fun screenToWorldRay(screenX: Double, screenY: Double): Pair<Vector3f, Vector3f> {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera

        try {
            val mc     = Minecraft.getInstance()
            val camera = mc.gameRenderer.mainCamera

            // ==== 用 Screen 的宽高 而不是 Window 的帧缓冲分辨率 ====
            val width  = this.width .toFloat()
            val height = this.height.toFloat()

            // 1. 真实 FOV 和宽高比
            val fovY   = Math.toRadians(mc.options.fov().get().toDouble()).toFloat()
            val aspect = width / height
            val fovX   = 2f * atan(tan(fovY / 2f) * aspect)

            // 2. NDC 坐标 [-1,1]
            val ndcX = (2f * screenX.toFloat() / width  - 1f)
            val ndcY = (1f - 2f * screenY.toFloat() / height)

            // 3. 本地偏移
            val offsetX = ndcX * tan(fovX / 2f)
            val offsetY = ndcY * tan(fovY / 2f)

            // 4. world-space 基向量
            val fwd   = Vector3f(camera.lookVector .x.toFloat(), camera.lookVector .y.toFloat(), camera.lookVector .z.toFloat())
            val up    = Vector3f(camera.upVector   .x.toFloat(), camera.upVector   .y.toFloat(), camera.upVector   .z.toFloat())
            val right = Vector3f(camera.leftVector.x.toFloat(), camera.leftVector.y.toFloat(), camera.leftVector.z.toFloat())
                .negate()

            // 5. 合成方向
            val dir = Vector3f(fwd)
                .add(Vector3f(right).mul(offsetX))
                .add(Vector3f(up   ).mul(offsetY))
                .normalize()

            // 6. 射线原点 = 相机世界位置
            val pos    = camera.position
            val origin = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

            return Pair(origin, dir)

        } catch (e: Exception) {
            // 如果计算出错，记录错误并返回一个默认射线
            println("Error calculating ray: ${e.message}")
            e.printStackTrace()

            // 返回一个默认射线（直视前方）
            val rayOrigin = Vector3f(camera.position.toVector3f())
            val rayDirection = Vector3f(
                -sin(Math.toRadians(camera.yRot.toDouble())).toFloat() * cos(Math.toRadians(camera.xRot.toDouble())).toFloat(),
                -sin(Math.toRadians(camera.xRot.toDouble())).toFloat(),
                cos(Math.toRadians(camera.yRot.toDouble())).toFloat() * cos(Math.toRadians(camera.xRot.toDouble())).toFloat()
            ).normalize()

            return Pair(rayOrigin, rayDirection)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // 记录拖拽开始位置
        dragStartX = mouseX
        dragStartY = mouseY

        // 优先处理其他 Widget
        if (treeView.mouseClicked(mouseX, mouseY, button)) return true
        if (modelSelectionList.mouseClicked(mouseX, mouseY, button)) return true
        if (animationSelectionList.mouseClicked(mouseX, mouseY, button)) return true

        // 处理 WebBrowserWidget 的点击
        if (webBrowserWidget.mouseClicked(mouseX, mouseY, button) == true) {
             treeView.isFocused = false
             modelSelectionList.isFocused = false
             animationSelectionList.isFocused = false
            // 设置屏幕的焦点，确保后续键盘事件能路由到它
            this.focused = webBrowserWidget // 让 Screen 知道谁是焦点
            return true
        }

        // Only handle left clicks in the 3D view area
        if (button == 0) {
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player ?: return super.mouseClicked(mouseX, mouseY, button)
            val animatable = player as? IAnimatable<*> ?: return super.mouseClicked(mouseX, mouseY, button)

            // Get ray from screen position
            val (rayOrigin, rayDirection) = screenToWorldRay(mouseX, mouseY)

            // 输出射线信息便于调试
            println("Player.pos: ${player.getPosition(1f)}, Ray origin: $rayOrigin, direction: $rayDirection")

            // 存储射线信息用于可视化
            debugRayOrigin = rayOrigin
            debugRayDirection = rayDirection

            // 转换为JME的Vector3f
            val jmeRayOrigin = JMEVector3f(rayOrigin.x, rayOrigin.y, rayOrigin.z)
            val jmeRayDirection = JMEVector3f(rayDirection.x, rayDirection.y, rayDirection.z)

            // 计算射线终点（射线长度设为100单位）
            val jmeRayEnd = JMEVector3f(jmeRayOrigin)
            jmeRayEnd.addLocal(jmeRayDirection.mult(100f))

            // 执行射线检测
            val results = ArrayList<PhysicsRayTestResult>()
            player.level().physicsLevel.world.rayTest(jmeRayOrigin, jmeRayEnd, results)

            // 如果有碰撞结果，处理它们
            if (results.isNotEmpty()) {
                // 按照距离排序
                results.sortBy { it.hitFraction }

                // 获取最近的碰撞结果
                val closestHit = results[0]
                val hitObjectName = closestHit.collisionObject.name

                println("Hit object: $hitObjectName")

                // 检查是否击中了Gizmo
                if (hitObjectName.startsWith("gizmo_")) {
                    // 解析轴名称
                    val axisName = hitObjectName.substringAfter("gizmo_")
                    val axis = when (axisName) {
                        "x" -> Axis.X
                        "y" -> Axis.Y
                        "z" -> Axis.Z
                        else -> null
                    }

                    if (axis != null) {
                        draggingAxis = axis
                        println("Dragging Gizmo Axis: $axis")

                        // Record original pivot for undo/redo
                        val targetElement = selectedCube ?: selectedBone
                        if (targetElement != null) {
                            originalPivotBeforeDrag = when (targetElement) {
                                is OBone -> targetElement.pivot
                                is OCube -> targetElement.pivot
                                else -> null
                            }
                            println("Started dragging $axis, original pivot: $originalPivotBeforeDrag")
                        }

                        return true // Event handled
                    }
                }
                // 检查是否击中了立方体
                else if (hitObjectName.startsWith("cube_")) {
                    // 解析立方体信息（格式：cube_骨骼名称_立方体索引）
                    val parts = hitObjectName.split("_", limit = 3)
                    if (parts.size >= 3) {
                        val boneName = parts[1]
                        val cubeIndex = parts[2].toIntOrNull()

                        if (cubeIndex != null) {
                            // 查找对应的骨骼和立方体
                            val bone = model?.bones?.get(boneName)
                            val cube = bone?.cubes?.getOrNull(cubeIndex)

                            if (bone != null && cube != null) {
                                // 立方体被点击 - 更新选择
                                selectedCube = cube
                                selectedBone = bone
                                draggingAxis = null
                                originalPivotBeforeDrag = null

                                // 更新TreeView选择
                                treeView.setSelectedElement(cube)

                                println("Selected cube in bone: ${bone.name}")
                                return true
                            }
                        }
                    }
                }
                // 检查是否击中了骨骼
                else {
                    // 解析骨骼名称
                    val boneName = hitObjectName
                    // 查找对应的骨骼
                    val bone = model?.bones?.get(boneName)
                    if (bone != null) {
                        // 骨骼被点击 - 更新选择
                        selectedBone = bone
                        selectedCube = null
                        draggingAxis = null
                        originalPivotBeforeDrag = null

                        // 更新TreeView选择
                        treeView.setSelectedElement(bone)

                        println("Selected bone: ${bone.name}")
                        return true
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    // --- Save Functionality (Remains mostly the same, uses internal 'model') ---
    fun saveModelToFile() {
        // ... (Save logic should still work as it uses the 'model' field)
        if (model == null) {
            minecraft?.player?.sendSystemMessage(Component.literal("Error: No model data loaded to save."))
            return
        }

        try {
            // 1. Construct the target file path
            val basePath = "F:/Work/code/test/Spark-Core/build/resources/main/data/" // TODO: Make path relative or configurable?
            val namespace = modelLocation.namespace
            val path = modelLocation.path // e.g., "geo/model/my_model" or just "my_model"
            // Ensure the path includes the necessary subdirectory if not already present
            val modelPath = if (path.startsWith("geo/model/")) path else "geo/model/$path"
            // Ensure .json extension only once
            val cleanModelPath = modelPath.removeSuffix(".json")
            val fullPath = "$basePath$namespace/$cleanModelPath.json"
            val targetFile = File(fullPath)

            // Ensure parent directories exist
            targetFile.parentFile?.mkdirs()

            // 2. Serialize the model to JSON
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(model) // Serialize the internal 'model' instance

            // 3. Write JSON to the file
            targetFile.writeText(jsonString, Charsets.UTF_8)

            // 4. Provide feedback
            minecraft?.player?.sendSystemMessage(Component.literal("Model saved successfully to: $fullPath"))
            println("Model saved to: $fullPath") // Also log to console

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Failed to save model to file", e)
            minecraft?.player?.sendSystemMessage(Component.literal("Error saving model: ${e.message}"))
        }
    }

    // --- Keyboard Input Handling (Remains the same) ---
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // 检查是否有获得焦点的 Widget 处理按键 (包括 WebBrowserWidget)
        // Screen -> AbstractContainerEventHandler -> AbstractWidget 会将事件传递给 this.focused
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true
        }

        // 处理 Undo/Redo 快捷键 (如果焦点不在 Widget 上，或者 Widget 未处理)
        if (SparkKeyMappings.MODEL_EDITOR_UNDO.matches(keyCode, scanCode)) {
            if (undoStack.isNotEmpty()) {
                val action = undoStack.pop()
                action.undo(treeView)
                redoStack.push(action)
                println("Undo performed via shortcut. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
                return true
            }
        }
        if (SparkKeyMappings.MODEL_EDITOR_REDO.matches(keyCode, scanCode)) {
            if (redoStack.isNotEmpty()) {
                val action = redoStack.pop()
                action.redo(treeView)
                undoStack.push(action)
                println("Redo performed via shortcut. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
                return true
            }
        }

        return false // 没有处理
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Handle widget releases first if necessary (though usually handled by click)
        if (treeView.mouseReleased(mouseX, mouseY, button)) return true
        if (modelSelectionList.mouseReleased(mouseX, mouseY, button)) return true
        if (animationSelectionList.mouseReleased(mouseX, mouseY, button)) return true


        // Handle Gizmo drag release
        if (button == 0 && draggingAxis != null) {
            println("Stopped dragging Gizmo Axis: $draggingAxis")
            val axisJustDragged = draggingAxis
            draggingAxis = null

            // --- Create EditAction (NEEDS REWORK) ---
            // This needs updated PivotEditAction and correct pivot checking
            /*
            val player = Minecraft.getInstance().player as? IAnimatable<*>
            val targetElement = selectedCube ?: selectedBone // Get the element that was dragged
            if (targetElement != null && originalPivotBeforeDrag != null && player != null) {
                 // Get current pivot from OModel (base state)
                val currentPivot = when (targetElement) {
                     is OBone -> model?.bones?.get(targetElement.name)?.pivot
                     is OCube -> model?.bones?.values
                                     ?.find { b -> b.cubes.any { c -> c == targetElement } }
                                     ?.cubes?.find { c -> c == targetElement }?.pivot
                    else -> null
                }

                // Compare with the pivot *before* the drag started
                if (currentPivot != null && currentPivot != originalPivotBeforeDrag) {
                    // TODO: Update PivotEditAction or create new action type if needed
                    // val action = PivotEditAction(targetElement, originalPivotBeforeDrag!!, currentPivot, model)
                    // undoStack.push(action)
                    // redoStack.clear()
                    // println("Pivot changed. Added action to undo stack. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
                    // treeView.refreshVisibleNodes()
                    println("TODO: Implement PivotEditAction creation")
                } else {
                    println("Pivot did not change significantly or could not be read.")
                }
            }
            */
            originalPivotBeforeDrag = null // Reset after drag ends

            return true // Event handled
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    // --- Edit Action for Undo/Redo (NEEDS REWORK for targetElement update) ---
    sealed interface EditAction {
        fun undo(treeView: ModelTreeViewWidget)
        fun redo(treeView: ModelTreeViewWidget)
    }

    // 需要审核PivotEditAction：它如何获取当前引用？
    // OBone/OCube within the potentially modified 'model'?
    // It might be better to store IDs/Names and look them up.
    data class PivotEditAction(
        val targetElement: Any, // OBone or OCube - Storing the object directly might be problematic if model updates replace instances
        val oldPivot: Vec3,
        val newPivot: Vec3,
        val modelRef: OModel? // Reference to the model for modification
    ) : EditAction {

        // TODO: Update setPivot to handle potential instance changes in modelRef.bones/cubes
        //       Maybe use names/paths to find the element to modify.
        private fun setPivot(pivot: Vec3) {
            when (targetElement) {
                is OBone -> {
                    // Find bone by name, assuming name is unique and stable
                    val boneToUpdate = modelRef?.bones?.get(targetElement.name)
                    if (boneToUpdate != null) {
                         // Create a new instance with the updated pivot
                        val updatedBone = boneToUpdate.copy(pivot = pivot)
                        modelRef.bones.put(targetElement.name, updatedBone)
                        // How to update screen's selectedBone if it was this one? Needs callback or direct access.
                    } else {
                        SparkCore.LOGGER.warn("Cannot find bone '${targetElement.name}' in model during pivot update.")
                    }
                }
                is OCube -> {
                    // Find the parent bone first, then the cube within it. This is fragile.
                     var parentBone: OBone? = null
                     var cubeIndex = -1
                     modelRef?.bones?.values?.forEach { bone ->
                         val index = bone.cubes.indexOfFirst { it == targetElement } // Relies on object identity or correct equals()
                         if (index != -1) {
                             parentBone = bone
                             cubeIndex = index
                             return@forEach // Exit loop once found
                         }
                     }

                    if (parentBone != null) {
                        val cubeToUpdate = parentBone.cubes[cubeIndex] // Get the potentially old instance
                        val updatedCube = cubeToUpdate.copy(pivot = pivot) // Create new instance
                        parentBone.cubes[cubeIndex] = updatedCube // Replace in list
                        // Update screen's selectedCube?
                    } else {
                        SparkCore.LOGGER.warn("Cannot find cube or its parent bone during pivot update.")
                    }
                }
                else -> SparkCore.LOGGER.warn("Unsupported element type for PivotEditAction: ${targetElement::class.simpleName}")
            }
        }

        override fun undo(treeView: ModelTreeViewWidget) {
            setPivot(oldPivot)
            treeView.refreshVisibleNodes() // Refresh TreeView after undo
        }

        override fun redo(treeView: ModelTreeViewWidget) {
            setPivot(newPivot)
            treeView.refreshVisibleNodes() // Refresh TreeView after redo
        }
    }

    @SubscribeEvent
    fun onRenderLevelStage(event: RenderLevelStageEvent) {
        // --- Change Render Stage back to AFTER_ENTITIES --- M
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return // Revert back to AFTER_ENTITIES
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen != this) return

        val player = minecraft.player ?: return
        val animatable = player as? IAnimatable<*> ?: return

        // Determine selected bone name (crucial for pivot and parent matrix)
        val selectedBoneName = selectedBone?.name ?: selectedCube?.let { cube ->
            model?.bones?.values?.find { bone -> bone.cubes.contains(cube) }?.name
        }
        if (selectedBoneName == null && selectedCube == null) return // Nothing selected

        val partialTick = event.partialTick.getGameTimeDeltaPartialTick(true)

        // --- Calculate Gizmo Position (Always based on the primary selected bone's pivot) ---
        val gizmoTargetBoneName = selectedBone?.name ?: selectedBoneName // Use selectedBone directly if available, else inferred name
        val pivotWorldPos = animatable.getWorldBonePivot(gizmoTargetBoneName ?: "") // Gizmo needs a bone pivot

        // --- Calculate Highlight Box Vertices (Recursively if a bone is selected) --- M
        val allCubeHighlightVerticesWorld: MutableList<List<Vector3f>> = mutableListOf()

        if (selectedCube != null && selectedBoneName != null) {
            // Case 1: A specific Cube is selected (No recursion needed)
            val parentBoneMatrix = animatable.getWorldBoneMatrix(selectedBoneName, partialTick)
            val cubeLocalMatrix = Matrix4f()
                .translate(selectedCube!!.pivot.toVector3f())
                .rotateZYX(selectedCube!!.rotation.toVector3f())
                .translate(selectedCube!!.pivot.toVector3f().negate())
                .translate(selectedCube!!.originPos.toVector3f())
            val cubeWorldMatrix = Matrix4f(parentBoneMatrix).mul(cubeLocalMatrix)
            val s = selectedCube!!.size.toVector3f()
            val vertices = listOf(
                Vector3f(0f, 0f, 0f), Vector3f(s.x, 0f, 0f), Vector3f(s.x, 0f, s.z), Vector3f(0f, 0f, s.z),
                Vector3f(0f, s.y, 0f), Vector3f(s.x, s.y, 0f), Vector3f(s.x, s.y, s.z), Vector3f(0f, s.y, s.z)
            ).map { cubeWorldMatrix.transformPosition(it, Vector3f()) }
            allCubeHighlightVerticesWorld.add(vertices)

        } else if (selectedBone != null) {
            // Case 2: A Bone is selected - Collect cubes recursively
            allCubeHighlightVerticesWorld.addAll(collectDescendantCubeVertices(selectedBone!!, animatable, partialTick))
        }

        // --- Prepare for World Rendering ---
        val poseStack = event.poseStack
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val cameraPos = event.camera.position
        poseStack.pushPose()
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
        val worldPoseMatrix = poseStack.last().pose()

        // --- Calculate Gizmo World Coordinates (Only endpoints needed) ---
        val axisLengthWorld = 0.4f
        val gizmoXEndWorld = Vector3f(pivotWorldPos).add(axisLengthWorld, 0f, 0f)
        val gizmoYEndWorld = Vector3f(pivotWorldPos).add(0f, axisLengthWorld, 0f)
        val gizmoZEndWorld = Vector3f(pivotWorldPos).add(0f, 0f, axisLengthWorld)


        try {
            // 设置完整的渲染状态，确保坐标轴和高亮框始终显示在最上层
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)  // 禁止写入深度缓冲区
            RenderSystem.enableBlend() // Enable blending
//            RenderSystem.defaultBlendFunc() // Use standard alpha blending
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA) // Explicit blend func if needed
            RenderSystem.polygonOffset(-1.0f, -10.0f)  // 负偏移使线条在前面
            RenderSystem.enablePolygonOffset()
            RenderSystem.lineWidth(1.5f)  // 设置更粗的线条宽度

            // --- Draw Highlight Boxes --- M
            if (allCubeHighlightVerticesWorld.isNotEmpty()) {
                val r = 0.2f
                val g = 0.6f
                val b = 1f
                val a = 0.8f // Slightly transparent highlight
                val highlightBuffer = bufferSource.getBuffer(RenderType.lines())
                allCubeHighlightVerticesWorld.forEach { wv -> // Iterate through each cube's vertices
                    if (wv.size == 8) { // Ensure we have 8 vertices
                        // 绘制12条边
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[0], wv[1], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[1], wv[2], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[2], wv[3], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[3], wv[0], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[4], wv[5], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[5], wv[6], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[6], wv[7], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[7], wv[4], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[0], wv[4], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[1], wv[5], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[2], wv[6], r, g, b, a)
                        drawWorldLine(worldPoseMatrix, highlightBuffer, wv[3], wv[7], r, g, b, a)
                    } else {
                        SparkCore.LOGGER.warn("Skipping highlight draw for element with invalid vertex count: ${wv.size}")
                    }
                }
                bufferSource.endBatch(RenderType.lines()) // Batch after drawing all boxes
            }

            // --- Draw Gizmo Axes --- M
            val gizmoBuffer = bufferSource.getBuffer(RenderType.lines())
            val gizmoAlpha = 1.0f // Opaque gizmo
            // 使用计算出的支点来绘制偏移，以对抗Z轴闪烁。
            drawWorldLineWithOffset(worldPoseMatrix, gizmoBuffer, pivotWorldPos, gizmoXEndWorld, 1f, 0.2f, 0.2f, gizmoAlpha) // Red X
            drawWorldLineWithOffset(worldPoseMatrix, gizmoBuffer, pivotWorldPos, gizmoYEndWorld, 0.2f, 1f, 0.2f, gizmoAlpha) // Green Y
            drawWorldLineWithOffset(worldPoseMatrix, gizmoBuffer, pivotWorldPos, gizmoZEndWorld, 0.2f, 0.2f, 1f, gizmoAlpha) // Blue Z
//
//            // 绘制调试射线
//            if (debugRayOrigin != null && debugRayDirection != null) {
//                val rayEnd = Vector3f(debugRayOrigin).add(Vector3f(debugRayDirection).mul(debugRayLength))
//                drawWorldLine(worldPoseMatrix, gizmoBuffer, debugRayOrigin!!, rayEnd, 1f, 1f, 0f, 1f) // 黄色射线
//
//                // 在射线起点和终点绘制小球体
//                drawDebugPoint(worldPoseMatrix, gizmoBuffer, debugRayOrigin!!, 0.2f, 1f, 0f, 0f, 1f) // 红色起点
//                drawDebugPoint(worldPoseMatrix, gizmoBuffer, rayEnd, 0.2f, 0f, 1f, 0f, 1f) // 绿色终点
//            }

            bufferSource.endBatch(RenderType.lines())

        } finally {
            // 恢复渲染状态
            RenderSystem.disablePolygonOffset()
            RenderSystem.lineWidth(1.0f)
            RenderSystem.disableBlend() // Disable blending
            RenderSystem.depthMask(true) // Re-enable depth writing
            RenderSystem.enableDepthTest() // Re-enable depth testing
            poseStack.popPose()
        }
    }

    //  递归收集cube顶点
    private fun collectDescendantCubeVertices(bone: OBone, animatable: IAnimatable<*>, partialTick: Float): List<List<Vector3f>> {
        val results = mutableListOf<List<Vector3f>>()
        val boneWorldMatrix = animatable.getWorldBoneMatrix(bone.name, partialTick) // Stop if bone matrix fails

        // 1. 从当前骨骼收集cube
        bone.cubes.forEach { cube ->
            val cubeLocalMatrix = Matrix4f()
                .translate(cube.pivot.toVector3f())
                .rotateZYX(cube.rotation.toVector3f())
                .translate(cube.pivot.toVector3f().negate())
                .translate(cube.originPos.toVector3f())
            val cubeWorldMatrix = Matrix4f(boneWorldMatrix).mul(cubeLocalMatrix)
            val s = cube.size.toVector3f()
            val vertices = listOf(
                Vector3f(0f, 0f, 0f), Vector3f(s.x, 0f, 0f), Vector3f(s.x, 0f, s.z), Vector3f(0f, 0f, s.z),
                Vector3f(0f, s.y, 0f), Vector3f(s.x, s.y, 0f), Vector3f(s.x, s.y, s.z), Vector3f(0f, s.y, s.z)
            ).map { cubeWorldMatrix.transformPosition(it, Vector3f()) }
            results.add(vertices)
        }

        // 2. Recursively collect from child bones
        val childBones = model?.bones?.values?.filter { it.parentName == bone.name } ?: emptyList()
        childBones.forEach { childBone ->
            results.addAll(collectDescendantCubeVertices(childBone, animatable, partialTick))
        }

        return results
    }

    // --- Helper to draw lines in WORLD space ---
    private fun drawWorldLine(poseMatrix: Matrix4f, buffer: VertexConsumer, start: Vector3f, end: Vector3f, r: Float, g: Float, b: Float, a: Float) {
        buffer.addVertex(poseMatrix, start.x, start.y, start.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, end.x, end.y, end.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
    }

    // --- 当两个面或线在同一深度时，会出现闪烁 ---
    private fun drawWorldLineWithOffset(poseMatrix: Matrix4f, buffer: VertexConsumer, start: Vector3f, end: Vector3f, r: Float, g: Float, b: Float, a: Float) {
        // 计算朝向摄像机的微小偏移向量
        val minecraft = Minecraft.getInstance()
        val cameraPos = minecraft.gameRenderer.mainCamera.position
        val midPoint = Vector3f(
            (start.x + end.x) / 2,
            (start.y + end.y) / 2,
            (start.z + end.z) / 2
        )
        val offsetDir = Vector3f(
            (cameraPos.x - midPoint.x).toFloat(),
            (cameraPos.y - midPoint.y).toFloat(),
            (cameraPos.z - midPoint.z).toFloat()
        )

        if (offsetDir.lengthSquared() > 0.0001f) {
            offsetDir.normalize().mul(0.001f) // 极小偏移量
        } else {
            offsetDir.set(0f, 0f, 0.001f) // 如果位置接近相机，使用默认偏移
        }

        // 应用偏移绘制
        val startOffset = Vector3f(start).add(offsetDir)
        val endOffset = Vector3f(end).add(offsetDir)
        buffer.addVertex(poseMatrix, startOffset.x, startOffset.y, startOffset.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, endOffset.x, endOffset.y, endOffset.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
    }

    // --- Helper to draw a debug point (small octahedron) ---
    private fun drawDebugPoint(poseMatrix: Matrix4f, buffer: VertexConsumer, position: Vector3f, size: Float, r: Float, g: Float, b: Float, a: Float) {
        val halfSize = size / 2f

        // 绘制八面体的八个三角形
        // 上半部分
        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, halfSize, 0f),
            Vector3f(position).add(halfSize, 0f, 0f),
            Vector3f(position).add(0f, 0f, halfSize),
            r, g, b, a)

        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, halfSize, 0f),
            Vector3f(position).add(0f, 0f, halfSize),
            Vector3f(position).add(-halfSize, 0f, 0f),
            r, g, b, a)

        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, halfSize, 0f),
            Vector3f(position).add(-halfSize, 0f, 0f),
            Vector3f(position).add(0f, 0f, -halfSize),
            r, g, b, a)

        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, halfSize, 0f),
            Vector3f(position).add(0f, 0f, -halfSize),
            Vector3f(position).add(halfSize, 0f, 0f),
            r, g, b, a)

        // 下半部分
        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, -halfSize, 0f),
            Vector3f(position).add(0f, 0f, halfSize),
            Vector3f(position).add(halfSize, 0f, 0f),
            r, g, b, a)

        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, -halfSize, 0f),
            Vector3f(position).add(-halfSize, 0f, 0f),
            Vector3f(position).add(0f, 0f, halfSize),
            r, g, b, a)

        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, -halfSize, 0f),
            Vector3f(position).add(0f, 0f, -halfSize),
            Vector3f(position).add(-halfSize, 0f, 0f),
            r, g, b, a)

        drawDebugTriangle(poseMatrix, buffer,
            Vector3f(position).add(0f, -halfSize, 0f),
            Vector3f(position).add(halfSize, 0f, 0f),
            Vector3f(position).add(0f, 0f, -halfSize),
            r, g, b, a)
    }

    // --- Helper to draw a triangle ---
    private fun drawDebugTriangle(poseMatrix: Matrix4f, buffer: VertexConsumer, v1: Vector3f, v2: Vector3f, v3: Vector3f, r: Float, g: Float, b: Float, a: Float) {
        // 绘制三角形的三条边
        buffer.addVertex(poseMatrix, v1.x, v1.y, v1.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, v2.x, v2.y, v2.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, v2.x, v2.y, v2.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, v3.x, v3.y, v3.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, v3.x, v3.y, v3.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, v1.x, v1.y, v1.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
    }

    // --- Animation Handling --- M
    private fun updateAndPlayIdleAnimation(animatable: IAnimatable<*>, animationSet: OAnimationSet) {
        // Fetch animation names from the provided OAnimationSet M
        currentAnimations = animationSet.animations.keys.toList()
        animationSelectionList.updateAnimations(currentAnimations)

        // Reset scroll for the animation list
        animationSelectionList.resetScroll()

        // Find and play idle animation
        val idleAnim = currentAnimations.firstOrNull { it.contains("idle", ignoreCase = true) }
        if (idleAnim != null) {
            playAnimation(idleAnim) // Use the common play function
        } else if (currentAnimations.isNotEmpty()) {
            // Maybe play the first animation as a fallback? Or stop?
             playAnimation(currentAnimations[0]) // Play first if no idle found
//            animatable.stopAllAnimations() // Or stop animations
        } else {
            // No animations found, stop any current ones
             animatable.stopAllAnimations()
        }
    }

    private fun playAnimation(animationName: String) {
        val player = Minecraft.getInstance().player ?: return
        val animatable = player as? IAnimatable<*> ?: return
        // Using playAnimationLoop, adjust if single play is needed
        try {
            val animInstance = AnimInstance.create(animatable, animationName)
            // Use a short transition time (e.g., 0 ticks) or adjust as needed
            animatable.animController.setAnimation(animInstance, 0)
        } catch (e: Exception) {
            // Catch potential errors if animation name is invalid (though list should be valid)
            SparkCore.LOGGER.error("Failed to play animation '$animationName'", e)
            minecraft?.gui?.chat?.addMessage(Component.literal("Error playing animation: ${e.message}"))
        }
        println("Playing animation: $animationName")
    }
}

private fun distSqr(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    // ... (implementation unchanged)
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
}

// --- Helper to get Animation Set Key from Model Location --- M
private fun getAnimationSetKey(modelLocation: ResourceLocation): ResourceLocation {
    // Mimics EntityAnimListener logic: namespace + path part before first slash
    val pathRoot = modelLocation.path.substringBefore('/')
    println(pathRoot)
    return ResourceLocation.withDefaultNamespace(pathRoot)
}

// --- IAnimatable Extension Placeholder --- M
// Assuming IAnimatable has methods like these based on JS extensions
// If not, these need to be adapted or implemented in IAnimatable interface/implementations
fun IAnimatable<*>.stopAllAnimations() {
    // Setting the animation to null on the controller should stop the current one
    try {
        this.animController.setAnimation(null, 0) // Use 0 transition time to stop immediately
    } catch (e: Exception) {
        SparkCore.LOGGER.error("Failed to stop animations", e)
    }
}

