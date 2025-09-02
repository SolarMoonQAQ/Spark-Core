package cn.solarmoon.spark_core.client.gui.screen

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.anim.play.layer.getMainLayer
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.client.gui.browser.WebBrowserWidget
import cn.solarmoon.spark_core.client.gui.screen.ModelEditorScreen.Axis.*
import cn.solarmoon.spark_core.client.gui.widget.ModelTreeViewWidget
import cn.solarmoon.spark_core.ik.component.IKComponent
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder.buildAnimationPathFromModel
import cn.solarmoon.spark_core.rpc.RpcClient
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil.normalizeResourceName
import com.cinemamod.mcef.MCEF
import com.google.gson.GsonBuilder
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
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
import org.lwjgl.opengl.GL11
import java.io.File
import java.util.*
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class ModelEditorScreen(private val modelLocation: ResourceLocation, private val textureLocation: ResourceLocation) :
    Screen(Component.translatable("${SparkCore.MOD_ID}.screen.model_editor.title")) {

    // --- Main Camera Control State (used by CameraMixin) ---
    var cameraDistance: Float = 5.0f
        private set
    var cameraYaw: Float = 0f
        private set
    var cameraPitch: Float = 15f
        private set
    private lateinit var physicsWorld: PhysicsWorld

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

    // --- Axis Selection and Manipulation ---
    private enum class Axis { X, Y, Z }

    private var selectedAxis: Axis? = null
    private var axisWorldCoordinates: Vector3f? = null
    private var axisDebugOffset: Vector3f? = null // For visualization during dragging
    private var axisClickDistance: Float = 0f // Distance from ray origin to axis when clicked

    // --- IK Mode ---
    private var ikModeEnabled: Boolean = false // 是否启用IK模式
    private var selectedIKChain: String? = null // 当前选中的IK链名称
    private var ikChains: Map<String, IKComponent> = emptyMap() // 可用的IK链
    private var selectedIKBones: MutableList<OBone> = mutableListOf() // IK链中的骨骼
    private var endEffectorBone: OBone? = null // 末端执行器骨骼
    private lateinit var ikChainSelectionList: IKChainSelectionList // IK链选择列表

    // --- IK Persistent Visualization ---
    private var persistentIKTargetPosition: Vector3f? = null // 持久化显示的IK目标位置
    private var persistentEndEffectorPosition: Vector3f? = null // 持久化显示的末端执行器实际位置
    private var editingIKTarget: Boolean = false // 是否正在编辑IK目标坐标轴
    private var ikTargetAxisSelected: Axis? = null // 选中的IK目标坐标轴

    // --- Model Selection List Widget (Inner class remains the same) ---
    private inner class ModelSelectionList(minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int) :
        ObjectSelectionList<ModelSelectionList.Entry>(minecraft, width, height, y, itemHeight) {

        init {
            // Populate the list
            OModel.ORIGINS.keys.sortedBy { it.toString() }.forEach { location ->
                this.addEntry(Entry(location))
            }
        }

        override fun getRowWidth(): Int =
            this.width - (if (this.maxScroll > 0) 18 else 12) // Adjust width for scrollbar

        override fun getScrollbarPosition(): Int = this.getX() + this.width - 6

        inner class Entry(val location: ResourceLocation) : ObjectSelectionList.Entry<Entry>() {
            private var lastClickTime: Long = 0
            private var lastClickLocation: ResourceLocation? = null

            override fun render(
                guiGraphics: GuiGraphics,
                index: Int,
                top: Int,
                left: Int,
                width: Int,
                height: Int,
                mouseX: Int,
                mouseY: Int,
                isHovering: Boolean,
                partialTick: Float
            ) {
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

    // --- IK Chain Selection List Widget ---
    private inner class IKChainSelectionList(minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int) :
        ObjectSelectionList<IKChainSelectionList.Entry>(minecraft, width, height, y, itemHeight) {

        fun updateIKChains(chains: Map<String, IKComponent>) {
            this.clearEntries() // 清除先前的条目
            chains.keys.sorted().forEach { chainName ->
                this.addEntry(Entry(chainName, chains[chainName]!!))
            }
            // 重置滚动位置
            this.scrollAmount = 0.0
        }

        override fun getRowWidth(): Int = this.width - (if (this.maxScroll > 0) 18 else 12)

        override fun getScrollbarPosition(): Int = this.x + this.width - 6

        inner class Entry(val chainName: String, val component: IKComponent) : ObjectSelectionList.Entry<Entry>() {
            override fun render(
                guiGraphics: GuiGraphics,
                index: Int,
                top: Int,
                left: Int,
                width: Int,
                height: Int,
                mouseX: Int,
                mouseY: Int,
                isHovering: Boolean,
                partialTick: Float
            ) {
                val font = this@ModelEditorScreen.font
                val color = if (chainName == selectedIKChain) 0xFFFF00 else 0xFFFFFF // 选中项显示为黄色
                guiGraphics.drawString(font, chainName, left + 2, top + (height - font.lineHeight) / 2, color)
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (button == 0) { // 左键点击
                    this@IKChainSelectionList.selected = this
                    selectIKChain(chainName)
                    return true
                }
                return false
            }

            override fun getNarration(): Component = Component.literal(chainName)
        }

        // 重置滚动位置
        fun resetScroll() {
            this.scrollAmount = 0.0
        }
    }

    // --- Animation Selection List Widget --- M
    private inner class AnimationSelectionList(minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int) :
        ObjectSelectionList<AnimationSelectionList.Entry>(minecraft, width, height, y, itemHeight) {

        fun updateAnimations(animationNames: List<String>) {
            this.clearEntries() // Clear previous animations
            animationNames.sorted().forEach { animName ->
                this.addEntry(Entry(animName))
            }
            // Optionally reset scroll position
            this.scrollAmount = 0.0
        }

        override fun getRowWidth(): Int = this.width - (if (this.maxScroll > 0) 18 else 12)

        override fun getScrollbarPosition(): Int = this.x + this.width - 6

        inner class Entry(val animationName: String) : ObjectSelectionList.Entry<Entry>() {
            override fun render(
                guiGraphics: GuiGraphics,
                index: Int,
                top: Int,
                left: Int,
                width: Int,
                height: Int,
                mouseX: Int,
                mouseY: Int,
                isHovering: Boolean,
                partialTick: Float
            ) {
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
        physicsWorld = player.level().physicsLevel.world
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
        val topMargin = 16
        val bottomMargin = 30 // Space for buttons

        val padding = 4 // Increased padding between elements
        val availableHeight = this.height - topMargin - bottomMargin

        val sidebarWidth = 100

        val modelListY = topMargin + padding + availableHeight / 2
        val modelListHeight = (availableHeight - padding) / 2

        val animListY = topMargin
        val animListHeight = (availableHeight - padding) / 2 // Let animation list take full height on left


        // --- Add Undo/Redo/Save/IK Buttons ---
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
                SparkCore.LOGGER.debug("Undo performed. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
            }
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // Redo Button
        addRenderableWidget(Button.builder(Component.literal("Redo")) { _ ->
            if (redoStack.isNotEmpty()) {
                val action = redoStack.pop()
                action.redo(treeView)
                undoStack.push(action)
                SparkCore.LOGGER.debug("Redo performed. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
            }
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // Save Button
        addRenderableWidget(Button.builder(Component.literal("Save")) { _ ->
            saveModelToFile()
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // IK Mode Button
        addRenderableWidget(Button.builder(Component.literal("IK Test")) { _ ->
            toggleIKMode()
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())

        // --- 8. Initialize Model Selection List ---
        // Placed below TreeView on the right sidebaranimListHeight
        modelSelectionList =
            ModelSelectionList(minecraft, sidebarWidth, modelListHeight, modelListY, font.lineHeight + 3)
        this.addRenderableWidget(modelSelectionList)

        // --- 8.5 Initialize Animation Selection List --- M
        // Place Animation List on the left side
        animationSelectionList =
            AnimationSelectionList(minecraft, sidebarWidth, animListHeight, animListY, font.lineHeight + 3)
        this.addRenderableWidget(animationSelectionList)

        // --- Initialize IK Chain Selection List ---
        // 初始化IK链选择列表，默认不显示
        ikChainSelectionList =
            IKChainSelectionList(minecraft, sidebarWidth, animListHeight, animListY, font.lineHeight + 3)
        ikChainSelectionList.visible = false // 默认不显示
        this.addRenderableWidget(ikChainSelectionList)


        val sidebarX = this.width - sidebarWidth // From backup
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
//            SparkCore.LOGGER.debug("Selected: ${selectedBone?.name ?: selectedCube?.let { "Cube in " + (model?.bones?.values?.find { b -> b.cubes.contains(it) }?.name ?: "Unknown") } ?: "None"}")
        }

        // Set root nodes (same logic)
        val rootNodes = model!!.bones.values
            .filter { it.parentName == null || model!!.bones[it.parentName] == null }
        treeView.setNodes(rootNodes, model!!)
        this.addRenderableWidget(treeView)

        // Populate initial animations and try auto-play
        // --- Fetch All Animations for Initial Model --- M
        updateAndPlayIdleAnimationForModel(animatable, modelLocation)

        // --- 9. Initialize Camera State ---
        // Initial camera state is now set in the properties directly
        // Reset yaw/pitch based on player potentially? Or keep fixed start?
        // Let's keep the fixed start for now. Player look dir might be distracting.
        // cameraYaw = player.yRot // Example: Start facing same way as player
        // cameraPitch = player.xRot

        // --- Add Web Browser Widget ---
        if (MCEF.isInitialized()) {
            val browserWidgetHeight = 70
            val browserWidgetWidth = this.width - sidebarWidth * 2
            val browserWidgetX = sidebarWidth
            val browserWidgetY = buttonY - browserWidgetHeight - 5

            webBrowserWidget = WebBrowserWidget(
                browserWidgetX, browserWidgetY,
                browserWidgetWidth, browserWidgetHeight,
                "https://www.baidu.com",
                transparent = true
            )
            this.addRenderableWidget(webBrowserWidget) // Add to screen
            SparkCore.LOGGER.debug("WebBrowserWidget added to ModelEditorScreen.") // Updated log message
        } else {
            SparkCore.LOGGER.debug("MCEF not initialized, skipping WebBrowserWidget.")
            minecraft.gui.chat.addMessage(Component.literal("Warning: MCEF not initialized, web browser widget disabled."))
        }

        // --- Register Event Listener --- M
        NeoForge.EVENT_BUS.register(this)
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
        val titleColor = if (ikModeEnabled) 0xFFFF00 else 0xFFFFFF // IK模式下标题显示为黄色
        val titleText =
            if (ikModeEnabled) "Editing: ${modelLocation.path} (IK Mode)" else "Editing: ${modelLocation.path}"
        guiGraphics.drawString(this.font, titleText, 10, 5, titleColor)

        // 显示IK模式信息
        if (ikModeEnabled) {
            // 显示选中的骨骼
            if (selectedIKBones.isNotEmpty()) {
                val boneNames = selectedIKBones.joinToString(", ") { it.name }
                val endEffectorName = endEffectorBone?.name ?: "None"
                guiGraphics.drawString(this.font, "Bones: $boneNames", 10, 29, 0xFFFFFF)
                guiGraphics.drawString(this.font, "End Effector: $endEffectorName", 10, 41, 0xFFFF00) // 黄色显示末端执行器
            }
        }

        // 显示选中轴的世界坐标
        if (selectedAxis != null && axisWorldCoordinates != null) {
            val axisName = when (selectedAxis) {
                X -> "X"
                Y -> "Y"
                Z -> "Z"
                null -> TODO()
            }

            // 格式化坐标显示，保留两位小数
            val formatCoord = { value: Float -> String.format("%.2f", value) }
            val worldPosStr = "(${formatCoord(axisWorldCoordinates!!.x)}, ${formatCoord(axisWorldCoordinates!!.y)}, ${
                formatCoord(axisWorldCoordinates!!.z)
            })"

            // 如果有偏移，显示当前位置
            var displayText = "$axisName Axis Origin: $worldPosStr"

            if (axisDebugOffset != null && (axisDebugOffset!!.x != 0f || axisDebugOffset!!.y != 0f || axisDebugOffset!!.z != 0f)) {
                val currentPos = Vector3f(axisWorldCoordinates).add(axisDebugOffset)
                val currentPosStr =
                    "(${formatCoord(currentPos.x)}, ${formatCoord(currentPos.y)}, ${formatCoord(currentPos.z)})"
                displayText += "\nCurrent Pos: $currentPosStr"

                // 在IK模式下显示额外信息
                if (ikModeEnabled && selectedIKChain != null) {
                    displayText += "\n\n\n\n\nIK Target for chain: $selectedIKChain"
                }
            }

            // 绘制坐标信息
            val textY = if (ikModeEnabled) 60 else 25 // IK模式下留出更多空间
            val lines = displayText.split("\n")
            for ((index, line) in lines.withIndex()) {
                guiGraphics.drawString(this.font, line, 10, textY + index * 12, 0xFFFFFF)
            }
        }

    }

    // --- Helper to draw screen lines (May still be useful if Gizmo draws on UI layer) ---
    private fun drawScreenLine(
        matrix: Matrix4f,
        normalMatrix: Matrix3f,
        buffer: VertexConsumer,
        start: Vector3f,
        end: Vector3f,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
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
            if (treeView.isMouseOver(dragStartX, dragStartY)) {
                return true
            }
        }
        // Check if dragging ModelSelectionList
        if (modelSelectionList.isMouseOver(mouseX, mouseY)) {
            if (modelSelectionList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true
            }
            if (modelSelectionList.isMouseOver(dragStartX, dragStartY)) {
                return true
            }
        }
        // Check if dragging AnimationSelectionList
        if (animationSelectionList.isMouseOver(mouseX, mouseY)) {
            if (animationSelectionList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true
            }
            if (animationSelectionList.isMouseOver(dragStartX, dragStartY)) {
                return true
            }
        }
        if (webBrowserWidget.isMouseOver(mouseX, mouseY)) {
            if (webBrowserWidget.isMouseOver(dragStartX, dragStartY)) {
                return true
            }
        }
        // If not dragging UI elements, control camera or Gizmo
        if (button == 0) { // Left button drag
            // If dragging Gizmo axis (needs update)
            if (draggingAxis != null && selectedAxis != null && axisWorldCoordinates != null) {
                // 处理坐标轴拖动
                handleAxisDrag(mouseX, mouseY, dragX, dragY)
                return true
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

    // --- Axis Drag Handling ---
    private fun handleAxisDrag(mouseX: Double, mouseY: Double, dragX: Double, dragY: Double) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val animatable = player as? IAnimatable<*> ?: return

        // 根据选中的轴确定移动方向
        val moveDirection = when (selectedAxis) {
            X -> Vector3f(1f, 0f, 0f)
            Y -> Vector3f(0f, 1f, 0f)
            Z -> Vector3f(0f, 0f, 1f)
            null -> return
        }

        // 计算屏幕移动幅度对应的世界坐标移动幅度
        // 这里使用一个简化的计算方法，根据鼠标移动的像素数量和当前视图来计算
        val camera = minecraft.gameRenderer.mainCamera
        val cameraRight = Vector3f(camera.leftVector.x, camera.leftVector.y, camera.leftVector.z).negate()
        val cameraUp = Vector3f(camera.upVector.x, camera.upVector.y, camera.upVector.z)

        // 计算移动幅度因子，这里的系数可以调整以控制敏感度
        val moveFactor = 0.01f

        // 计算移动向量，结合水平和垂直移动
        val horizontalMove = cameraRight.dot(moveDirection) * dragX.toFloat() * moveFactor
        val verticalMove = cameraUp.dot(moveDirection) * -dragY.toFloat() * moveFactor

        // 组合移动向量
        val totalMove = horizontalMove + verticalMove

        // 更新调试偏移量
        if (axisDebugOffset == null) {
            axisDebugOffset = Vector3f(0f, 0f, 0f)
        }

        // 根据选中的轴更新偏移量
        when (selectedAxis) {
            X -> axisDebugOffset!!.add(totalMove, 0f, 0f)
            Y -> axisDebugOffset!!.add(0f, totalMove, 0f)
            Z -> axisDebugOffset!!.add(0f, 0f, totalMove)
            null -> {}
        }

        // 输出当前世界坐标和偏移量
        val worldPos = axisWorldCoordinates
        val offset = axisDebugOffset
        if (worldPos != null && offset != null) {
            val axisName = when (selectedAxis) {
                X -> "X"
                Y -> "Y"
                Z -> "Z"
                null -> "?"
            }

            // 计算当前位置（原始位置 + 偏移）
            val currentPos = Vector3f(worldPos).add(offset)
            SparkCore.LOGGER.trace(
                "{} axis: World pos = {}, Current pos = {}, Offset = {}",
                axisName,
                worldPos,
                currentPos,
                offset
            )

            // 在IK模式下，更新IK目标位置
            if (ikModeEnabled && selectedIKChain != null) {
                val component = ikChains[selectedIKChain] ?: return

                // 将Vector3f转换为JME的Vector3f
                val jmeCurrentPos = currentPos.toBVector3f()

                // 更新IK组件的目标位置
                component.updateTargetWorldPosition(jmeCurrentPos)
            }
        }
    }

    // --- Get Element Matrix (NEEDS REWORK - Use Player) ---
    // private fun getElementWorldMatrix(partialTick: Float): Matrix4f? { ... } // COMMENTED OUT

    // --- Apply Displacement (NEEDS REWORK - Use Player, Recalculate Logic) ---
    // private fun applyWorldDisplacement(worldDisplacement: Vector3f) { ... } // COMMENTED OUT

    // --- Get Parent Matrix (NEEDS REWORK - Use Player) ---
    // private fun getParentWorldMatrix(boneName: String, animatable: IAnimatable<*>, partialTick: Float): Matrix4f? { ... } // COMMENTED OUT

    // --- Model Switching Logic ---
    private fun switchModel(newModelLocation: ResourceLocation) {
        SparkCore.LOGGER.debug("Switching model to: {}", newModelLocation)
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return // Need player

        // 1. Get the new OModel from cache
        val newOModel = OModel.ORIGINS[newModelLocation]
        if (newOModel == null) {
            minecraft.gui.chat.addMessage(Component.literal("Error: Could not find model $newModelLocation in ORIGINS cache."))
            return
        }

        // 2. Infer textureLocation using SparkResourcePathBuilder
        val newTextureLocation = try {
            // 从模型路径推断贴图路径：modId:modId/module/models/entity -> modId:module/textures/entity/entity
            val pathParts = newModelLocation.path.split("/")
            val moduleName = pathParts[0]
            val entityName = pathParts.drop(2).joinToString("/")
            SparkResourcePathBuilder.buildTexturePath(
                newModelLocation.namespace,
                moduleName,
                "entity/$entityName"
            )
        } catch (e: Exception) {
            SparkCore.LOGGER.error("Failed to infer texture location for model $newModelLocation", e)
            this.textureLocation // Fallback to original texture
        }
        SparkCore.LOGGER.debug("Inferred texture location: {}", newTextureLocation)

        // 3. Update internal model reference (for TreeView, Save, EditAction)
        this.model = newOModel
        // Don't update this.modelLocation/textureLocation (constructor vals)

        // 4. Update *player's* model index
        val animatable = player as? IAnimatable<*>
        if (animatable != null) {
            try {
                // Set the model index on the player entity
                animatable.modelIndex = ModelIndex(newModelLocation, newTextureLocation)
                RpcClient.loadModel(newModelLocation.toString())
            } catch (e: Exception) {
                minecraft.gui.chat.addMessage(Component.literal("Error setting model index on player: ${e.message}"))
            }
        } else {
            minecraft.gui.chat.addMessage(Component.literal("Error: Player is not IAnimatable."))
        }

        // 5. Update TreeView (same logic)
        val rootNodes = model!!.bones.values
            .filter { it.parentName == null || model!!.bones[it.parentName] == null }
        treeView.setNodes(rootNodes, model!!)

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
        // --- Fetch All Animations for New Model --- M
        if (animatable != null) {
            updateAndPlayIdleAnimationForModel(animatable, newModelLocation)
        }
        SparkCore.LOGGER.debug("Model switched successfully.")
    }

    // --- Restore mouseScrolled from Backup --- M
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // 优先让 WebBrowserWidget 处理滚动
        if (webBrowserWidget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
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
            val mc = Minecraft.getInstance()
            val camera = mc.gameRenderer.mainCamera

            // ==== 用 Screen 的宽高 而不是 Window 的帧缓冲分辨率 ====
            val width = this.width.toFloat()
            val height = this.height.toFloat()

            // 1. 真实 FOV 和宽高比
            val fovY = Math.toRadians(mc.options.fov().get().toDouble()).toFloat()
            val aspect = width / height
            val fovX = 2f * atan(tan(fovY / 2f) * aspect)

            // 2. NDC 坐标 [-1,1]
            val ndcX = (2f * screenX.toFloat() / width - 1f)
            val ndcY = (1f - 2f * screenY.toFloat() / height)

            // 3. 本地偏移
            val offsetX = ndcX * tan(fovX / 2f)
            val offsetY = ndcY * tan(fovY / 2f)

            // 4. world-space 基向量
            val fwd = Vector3f(camera.lookVector.x, camera.lookVector.y, camera.lookVector.z)
            val up = Vector3f(camera.upVector.x, camera.upVector.y, camera.upVector.z)
            val right = Vector3f(camera.leftVector.x, camera.leftVector.y, camera.leftVector.z)
                .negate()

            // 5. 合成方向
            val dir = Vector3f(fwd)
                .add(Vector3f(right).mul(offsetX))
                .add(Vector3f(up).mul(offsetY))
                .normalize()

            // 6. 射线原点 = 相机世界位置
            val pos = camera.position
            val origin = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

            return Pair(origin, dir)

        } catch (e: Exception) {
            // 如果计算出错，记录错误并返回一个默认射线
            SparkCore.LOGGER.debug("Error calculating ray: ${e.message}")
            e.printStackTrace()

            // 返回一个默认射线（直视前方）
            val origin = Vector3f(camera.position.toVector3f())
            val dir = Vector3f(
                -sin(Math.toRadians(camera.yRot.toDouble())).toFloat() * cos(Math.toRadians(camera.xRot.toDouble())).toFloat(),
                -sin(Math.toRadians(camera.xRot.toDouble())).toFloat(),
                cos(Math.toRadians(camera.yRot.toDouble())).toFloat() * cos(Math.toRadians(camera.xRot.toDouble())).toFloat()
            ).normalize()

            return Pair(origin, dir)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // 记录拖拽开始位置
        dragStartX = mouseX
        dragStartY = mouseY

        // 优先处理其他 Widget
        if (treeView.mouseClicked(mouseX, mouseY, button)) return true
        if (modelSelectionList.mouseClicked(mouseX, mouseY, button)) return true

        if (ikModeEnabled) {
            if (ikChainSelectionList.mouseClicked(mouseX, mouseY, button)) return true
        } else {
            if (animationSelectionList.mouseClicked(mouseX, mouseY, button)) return true
        }

        // 处理 WebBrowserWidget 的点击
        if (webBrowserWidget.mouseClicked(mouseX, mouseY, button)) {
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
            SparkCore.LOGGER.debug(
                "Player.pos: {}, Ray origin: {}, direction: {}",
                player.getPosition(1f),
                rayOrigin,
                rayDirection
            )

            // 存储射线信息用于可视化
            debugRayOrigin = rayOrigin
            debugRayDirection = rayDirection

            // 转换为JME的Vector3f
            val jmeRayOrigin = Vector3f(rayOrigin.x, rayOrigin.y, rayOrigin.z)
            val jmeRayDirection = Vector3f(rayDirection.x, rayDirection.y, rayDirection.z)

            // 计算射线终点（射线长度设为100单位）
            val jmeRayEnd = Vector3f(jmeRayOrigin)
            jmeRayEnd.add(jmeRayDirection.mul(100f, Vector3f()))

            // 执行射线检测
            val results = ArrayList<PhysicsRayTestResult>()
            physicsWorld.rayTest(jmeRayOrigin.toBVector3f(), jmeRayEnd.toBVector3f(), results)

            // 首先检查是否点击了坐标轴
            // 在IK模式下，检查末端执行器骨骼的坐标轴和IK目标坐标轴
            // 在正常模式下，检查选中骨骼的坐标轴
            val shouldCheckAxis = if (ikModeEnabled) {
                endEffectorBone != null && selectedIKChain != null
            } else {
                selectedBone != null
            }

            // 在IK模式下，检查是否点击了持久化的IK目标坐标轴
            if (ikModeEnabled && persistentIKTargetPosition != null) {
                // 计算IK目标坐标轴端点
                val targetAxisLength = 0.25f
                val targetXEnd = Vector3f(persistentIKTargetPosition).add(targetAxisLength, 0f, 0f)
                val targetYEnd = Vector3f(persistentIKTargetPosition).add(0f, targetAxisLength, 0f)
                val targetZEnd = Vector3f(persistentIKTargetPosition).add(0f, 0f, targetAxisLength)

                // 计算射线与各轴的最近距离
                val (distToXAxis, xAxisPoint) = rayDistanceToLineSegment(
                    rayOrigin,
                    rayDirection,
                    persistentIKTargetPosition!!,
                    targetXEnd
                )
                val (distToYAxis, yAxisPoint) = rayDistanceToLineSegment(
                    rayOrigin,
                    rayDirection,
                    persistentIKTargetPosition!!,
                    targetYEnd
                )
                val (distToZAxis, zAxisPoint) = rayDistanceToLineSegment(
                    rayOrigin,
                    rayDirection,
                    persistentIKTargetPosition!!,
                    targetZEnd
                )

                // 设置阈值，决定是否认为射线击中了坐标轴
                val hitThreshold = 0.2f

                // 找出最近的轴
                var closestAxis: Axis? = null
                var closestDist = Float.MAX_VALUE
                var closestPoint: Vector3f? = null

                if (distToXAxis < hitThreshold && distToXAxis < closestDist) {
                    closestAxis = X
                    closestDist = distToXAxis
                    closestPoint = xAxisPoint
                }

                if (distToYAxis < hitThreshold && distToYAxis < closestDist) {
                    closestAxis = Y
                    closestDist = distToYAxis
                    closestPoint = yAxisPoint
                }

                if (distToZAxis < hitThreshold && distToZAxis < closestDist) {
                    closestAxis = Z
                    closestDist = distToZAxis
                    closestPoint = zAxisPoint
                }

                // 如果找到了最近的轴，选中它
                if (closestAxis != null && closestPoint != null) {
                    ikTargetAxisSelected = closestAxis
                    draggingAxis = closestAxis
                    editingIKTarget = true
                    axisWorldCoordinates = persistentIKTargetPosition
                    axisDebugOffset = Vector3f(0f, 0f, 0f) // 初始无偏移
                    axisClickDistance = closestDist

                    // 计算从射线原点到交点的距离，用于后续拖动计算
                    val rayToPointVec = Vector3f(closestPoint).sub(rayOrigin)
                    axisClickDistance = rayToPointVec.length()

                    SparkCore.LOGGER.debug(
                        "Selected IK target axis: {}, World coordinates: {}",
                        closestAxis,
                        persistentIKTargetPosition
                    )
                    return true
                }
            }

            if (shouldCheckAxis) {
                // 确定要检查的骨骼名称
                val gizmoTargetBoneName = if (ikModeEnabled && endEffectorBone != null) {
                    endEffectorBone?.name
                } else {
                    selectedBone?.name
                }

                val pivotWorldPos = animatable.getWorldBonePivot(gizmoTargetBoneName ?: "")

                // 计算坐标轴端点
                val axisLengthWorld = 0.4f
                val gizmoXEndWorld = Vector3f(pivotWorldPos).add(axisLengthWorld, 0f, 0f)
                val gizmoYEndWorld = Vector3f(pivotWorldPos).add(0f, axisLengthWorld, 0f)
                val gizmoZEndWorld = Vector3f(pivotWorldPos).add(0f, 0f, axisLengthWorld)


                // 计算射线与各轴的最近距离
                val (distToXAxis, xAxisPoint) = rayDistanceToLineSegment(
                    rayOrigin,
                    rayDirection,
                    pivotWorldPos,
                    gizmoXEndWorld
                )
                val (distToYAxis, yAxisPoint) = rayDistanceToLineSegment(
                    rayOrigin,
                    rayDirection,
                    pivotWorldPos,
                    gizmoYEndWorld
                )
                val (distToZAxis, zAxisPoint) = rayDistanceToLineSegment(
                    rayOrigin,
                    rayDirection,
                    pivotWorldPos,
                    gizmoZEndWorld
                )

                // 设置阈值，决定是否认为射线击中了坐标轴
                val hitThreshold = 0.2f // 可以根据需要调整

                // 找出最近的轴
                var closestAxis: Axis? = null
                var closestDist = Float.MAX_VALUE
                var closestPoint: Vector3f? = null

                if (distToXAxis < hitThreshold && distToXAxis < closestDist) {
                    closestAxis = X
                    closestDist = distToXAxis
                    closestPoint = xAxisPoint
                }

                if (distToYAxis < hitThreshold && distToYAxis < closestDist) {
                    closestAxis = Y
                    closestDist = distToYAxis
                    closestPoint = yAxisPoint
                }

                if (distToZAxis < hitThreshold && distToZAxis < closestDist) {
                    closestAxis = Z
                    closestDist = distToZAxis
                    closestPoint = zAxisPoint
                }

                // 如果找到了最近的轴，选中它
                if (closestAxis != null && closestPoint != null) {
                    selectedAxis = closestAxis
                    draggingAxis = closestAxis
                    axisWorldCoordinates = pivotWorldPos
                    axisDebugOffset = Vector3f(0f, 0f, 0f) // 初始无偏移
                    axisClickDistance = closestDist

                    // 计算从射线原点到交点的距离，用于后续拖动计算
                    val rayToPointVec = Vector3f(closestPoint).sub(rayOrigin)
                    axisClickDistance = rayToPointVec.length()

                    SparkCore.LOGGER.debug("Selected axis: {}, World coordinates: {}", closestAxis, pivotWorldPos)
                    return true
                }
            }

            // 如果没有点击坐标轴，检查是否点击了骨骼
            if (results.isNotEmpty()) {
                // 按照距离排序
                results.sortBy { it.hitFraction }

                // 获取最近的碰撞结果
                val closestHit = results[0]
                val hitObjectName = closestHit.collisionObject.name

                SparkCore.LOGGER.debug("Hit object: $hitObjectName")
                // 解析骨骼名称
                val boneName = hitObjectName
                // 查找对应的骨骼
                val bone = model?.bones?.get(boneName)
                if (bone != null) {
                    // 骨骼被点击 - 更新选择
                    selectedBone = bone
                    selectedCube = null
                    draggingAxis = null
                    selectedAxis = null
                    axisWorldCoordinates = null
                    axisDebugOffset = null
                    originalPivotBeforeDrag = null

                    // 更新TreeView选择
                    treeView.setSelectedElement(bone)

                    SparkCore.LOGGER.debug("Selected bone: ${bone.name}")
                    return true
                }
            }
        }

        // 如果点击了其他地方，重置轴选择状态
        if (button == 0) {
            // 在IK模式下，不重置轴选择状态，以便持久化显示
            if (!ikModeEnabled) {
                selectedAxis = null
                axisWorldCoordinates = null
                axisDebugOffset = null
                draggingAxis = null
            } else {
                // 在IK模式下，只重置拖动状态，保留选择的轴和坐标
                draggingAxis = null
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
            val basePath =
                "F:/Work/code/test/Spark-Core/build/resources/main/data/" // TODO: Make path relative or configurable?
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
            SparkCore.LOGGER.debug("Model saved to: $fullPath") // Also log to console

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
        if (SparkKeyMappings.MODEL_EDITOR_UNDO.get().matches(keyCode, scanCode)) {
            if (undoStack.isNotEmpty()) {
                val action = undoStack.pop()
                action.undo(treeView)
                redoStack.push(action)
                SparkCore.LOGGER.debug("Undo performed via shortcut. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
                return true
            }
        }
        if (SparkKeyMappings.MODEL_EDITOR_REDO.get().matches(keyCode, scanCode)) {
            if (redoStack.isNotEmpty()) {
                val action = redoStack.pop()
                action.redo(treeView)
                undoStack.push(action)
                SparkCore.LOGGER.debug("Redo performed via shortcut. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
                return true
            }
        }

        return false // 没有处理
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Handle widget releases first if necessary (though usually handled by click)
//        if (treeView.mouseReleased(mouseX, mouseY, button)) return true
        if (modelSelectionList.mouseReleased(mouseX, mouseY, button)) return true
        if (animationSelectionList.mouseReleased(mouseX, mouseY, button)) return true


        // Handle Gizmo drag release
        if (button == 0 && draggingAxis != null) {
            SparkCore.LOGGER.debug("Stopped dragging Gizmo Axis: {}, Final offset: {}", draggingAxis, axisDebugOffset)
            val axisJustDragged = draggingAxis
            draggingAxis = null
            // 保留 selectedAxis 和 axisWorldCoordinates 以便显示坐标

            // 如果正在编辑IK目标坐标轴
            if (editingIKTarget && ikModeEnabled && selectedIKChain != null && axisWorldCoordinates != null && axisDebugOffset != null) {
                // 更新IK目标位置（原始位置+偏移）
                persistentIKTargetPosition = Vector3f(axisWorldCoordinates).add(axisDebugOffset)

                val minecraft = Minecraft.getInstance()
                val player = minecraft.player
                val animatable = player as? IAnimatable<*>

                if (animatable != null && endEffectorBone != null) {
                    // 更新末端执行器的实际位置
                    persistentEndEffectorPosition = animatable.getWorldBonePivot(endEffectorBone!!.name)

                    SparkCore.LOGGER.debug(
                        "更新IK目标坐标轴 - 新目标位置: {}, 末端位置: {}",
                        persistentIKTargetPosition,
                        persistentEndEffectorPosition
                    )
                }

                // 重置编辑状态，但保留选中的轴
                editingIKTarget = false
                selectedAxis = ikTargetAxisSelected
                axisWorldCoordinates = persistentIKTargetPosition
                axisDebugOffset = Vector3f(0f, 0f, 0f)
            }
            // 在IK模式下，保存当前IK目标位置和末端执行器位置用于持久化显示
            else if (ikModeEnabled && selectedIKChain != null && axisWorldCoordinates != null && axisDebugOffset != null) {
                val minecraft = Minecraft.getInstance()
                val player = minecraft.player
                val animatable = player as? IAnimatable<*>

                if (animatable != null && endEffectorBone != null) {
                    // 保存当前IK目标位置（原始位置+偏移）
                    persistentIKTargetPosition = Vector3f(axisWorldCoordinates).add(axisDebugOffset)

                    // 保存末端执行器的实际位置
                    persistentEndEffectorPosition = animatable.getWorldBonePivot(endEffectorBone!!.name)

                    SparkCore.LOGGER.debug(
                        "保存IK持久化显示 - 目标位置: {}, 末端位置: {}",
                        persistentIKTargetPosition,
                        persistentEndEffectorPosition
                    )
                }
            }

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
                    // SparkCore.LOGGER.debug("Pivot changed. Added action to undo stack. Undo: ${undoStack.size}, Redo: ${redoStack.size}")
                    // treeView.refreshVisibleNodes()
                    SparkCore.LOGGER.debug("TODO: Implement PivotEditAction creation")
                } else {
                    SparkCore.LOGGER.debug("Pivot did not change significantly or could not be read.")
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

//    //TODO: 服务端可用, 如何修改原文件?
//    data class PivotEditAction(
//        val targetElement: Any, // OBone or OCube - Storing the object directly might be problematic if model updates replace instances
//        val oldPivot: Vec3,
//        val newPivot: Vec3,
//        val modelRef: OModel? // Reference to the model for modification
//    ) : EditAction {
//
//        // TODO: Update setPivot to handle potential instance changes in modelRef.bones/cubes
//        //       Maybe use names/paths to find the element to modify.
//        private fun setPivot(pivot: Vec3) {
//            when (targetElement) {
//                is OBone -> {
//                    // Find bone by name, assuming name is unique and stable
//                    val boneToUpdate = modelRef?.bones?.get(targetElement.name)
//                    if (boneToUpdate != null) {
//                         // Create a new instance with the updated pivot
//                        val updatedBone = boneToUpdate.copy(pivot = pivot)
//                        modelRef.bones.put(targetElement.name, updatedBone)
//                        // How to update screen's selectedBone if it was this one? Needs callback or direct access.
//                    } else {
//                        SparkCore.LOGGER.warn("Cannot find bone '${targetElement.name}' in model during pivot update.")
//                    }
//                }
//                is OCube -> {
//                    // Find the parent bone first, then the cube within it. This is fragile.
//                     var parentBone: OBone? = null
//                     var cubeIndex = -1
//                     modelRef?.bones?.values?.forEach { bone ->
//                         val index = bone.cubes.indexOfFirst { it == targetElement } // Relies on object identity or correct equals()
//                         if (index != -1) {
//                             parentBone = bone
//                             cubeIndex = index
//                             return@forEach // Exit loop once found
//                         }
//                     }
//
//                    if (parentBone != null) {
//                        val cubeToUpdate = parentBone.cubes[cubeIndex] // Get the potentially old instance
//                        val updatedCube = cubeToUpdate.copy(pivot = pivot) // Create new instance
//                        parentBone.cubes[cubeIndex] = updatedCube // Replace in list
//                        // Update screen's selectedCube?
//                    } else {
//                        SparkCore.LOGGER.warn("Cannot find cube or its parent bone during pivot update.")
//                    }
//                }
//                else -> SparkCore.LOGGER.warn("Unsupported element type for PivotEditAction: ${targetElement::class.simpleName}")
//            }
//        }
//
//        override fun undo(treeView: ModelTreeViewWidget) {
//            setPivot(oldPivot)
//            treeView.refreshVisibleNodes() // Refresh TreeView after undo
//        }
//
//        override fun redo(treeView: ModelTreeViewWidget) {
//            setPivot(newPivot)
//            treeView.refreshVisibleNodes() // Refresh TreeView after redo
//        }
//    }

    @SubscribeEvent
    fun onRenderLevelStage(event: RenderLevelStageEvent) {
        // --- Change Render Stage back to AFTER_ENTITIES --- M
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return // Revert back to AFTER_ENTITIES
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen != this) return

        val player = minecraft.player ?: return
        val animatable = player as? IAnimatable<*> ?: return

        // 确定选中的骨骼名称（对于透视和父矩阵至关重要）
        val selectedBoneName = if (ikModeEnabled && endEffectorBone != null) {
            // IK模式下，使用末端执行器骨骼
            endEffectorBone?.name
        } else {
            // 正常模式下，使用选中的骨骼或方块的父骨骼
            selectedBone?.name ?: selectedCube?.let { cube ->
                model?.bones?.values?.find { bone -> bone.cubes.contains(cube) }?.name
            }
        }

        // 如果没有选中骨骼或方块，则返回
        if (selectedBoneName == null && selectedCube == null && !ikModeEnabled) return // Nothing selected

        // 如果在IK模式下没有选中链，则返回
        if (ikModeEnabled && selectedIKChain == null) return // No IK chain selected

        val partialTick = event.partialTick.getGameTimeDeltaPartialTick(true)

        // --- Calculate Gizmo Position (Always based on the primary selected bone's pivot) ---
        val gizmoTargetBoneName =
            selectedBone?.name ?: selectedBoneName // Use selectedBone directly if available, else inferred name
        val pivotWorldPos = animatable.getWorldBonePivot(gizmoTargetBoneName ?: "") // Gizmo needs a bone pivot

        // --- Calculate Highlight Box Vertices (Recursively if a bone is selected) --- M
        val allCubeHighlightVerticesWorld: MutableList<List<Vector3f>> = mutableListOf()
        val boneHighlightColors: MutableMap<List<Vector3f>, Triple<Float, Float, Float>> = mutableMapOf()

        if (ikModeEnabled) {
            // IK模式下，高亮显示IK链中的所有骨骼
            for (bone in selectedIKBones) {
                val vertices = collectDescendantCubeVertices(bone, animatable, partialTick)
                allCubeHighlightVerticesWorld.addAll(vertices)

                // 对末端执行器骨骼使用黄色，其他骨骼使用蓝色
                val color = if (bone == endEffectorBone) {
                    Triple(1f, 1f, 0.2f) // 黄色
                } else {
                    Triple(0.2f, 0.6f, 1f) // 蓝色
                }

                // 为每个骨骼的顶点列表设置颜色
                vertices.forEach { vertexList ->
                    boneHighlightColors[vertexList] = color
                }
            }
        } else {
            // 正常模式下的高亮显示
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
                boneHighlightColors[vertices] = Triple(0.2f, 0.6f, 1f) // 蓝色

            } else if (selectedBone != null) {
                // Case 2: A Bone is selected - Collect cubes recursively
                val vertices = collectDescendantCubeVertices(selectedBone!!, animatable, partialTick)
                allCubeHighlightVerticesWorld.addAll(vertices)

                // 所有骨骼使用蓝色
                vertices.forEach { vertexList ->
                    boneHighlightColors[vertexList] = Triple(0.2f, 0.6f, 1f) // 蓝色
                }
            }
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
                val defaultColor = Triple(0.2f, 0.6f, 1f) // 默认蓝色
                val a = 0.8f // Slightly transparent highlight
                val highlightBuffer = bufferSource.getBuffer(RenderType.lines())
                allCubeHighlightVerticesWorld.forEach { wv -> // Iterate through each cube's vertices
                    if (wv.size == 8) { // Ensure we have 8 vertices
                        // 获取骨骼的高亮颜色，如果没有设置则使用默认颜色
                        val (r, g, b) = boneHighlightColors[wv] ?: defaultColor

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

            // 在IK模式下，只为末端执行器骨骼显示坐标轴
            val shouldDrawGizmo = if (ikModeEnabled) {
                endEffectorBone != null && selectedIKChain != null
            } else {
                selectedBone != null || selectedCube != null
            }

            if (shouldDrawGizmo) {
                // 确定坐标轴的位置
                val gizmoPos = if (ikModeEnabled && endEffectorBone != null) {
                    // 在IK模式下，使用末端执行器骨骼的位置
                    animatable.getWorldBonePivot(endEffectorBone!!.name)
                } else {
                    // 在正常模式下，使用选中骨骼的位置
                    pivotWorldPos
                }

                // 计算坐标轴端点
                val axisLengthWorld = 0.4f
                val gizmoXEndWorld = Vector3f(gizmoPos).add(axisLengthWorld, 0f, 0f)
                val gizmoYEndWorld = Vector3f(gizmoPos).add(0f, axisLengthWorld, 0f)
                val gizmoZEndWorld = Vector3f(gizmoPos).add(0f, 0f, axisLengthWorld)

                // 绘制坐标轴，如果轴被选中则使用更亮的颜色
                // X轴 - 红色
                val xAxisColor = if (selectedAxis == X) Triple(1f, 0.8f, 0.8f) else Triple(1f, 0.2f, 0.2f)
                drawWorldLineWithOffset(
                    worldPoseMatrix,
                    gizmoBuffer,
                    gizmoPos,
                    gizmoXEndWorld,
                    xAxisColor.first,
                    xAxisColor.second,
                    xAxisColor.third,
                    gizmoAlpha
                )

                // Y轴 - 绿色
                val yAxisColor = if (selectedAxis == Y) Triple(0.8f, 1f, 0.8f) else Triple(0.2f, 1f, 0.2f)
                drawWorldLineWithOffset(
                    worldPoseMatrix,
                    gizmoBuffer,
                    gizmoPos,
                    gizmoYEndWorld,
                    yAxisColor.first,
                    yAxisColor.second,
                    yAxisColor.third,
                    gizmoAlpha
                )

                // Z轴 - 蓝色
                val zAxisColor = if (selectedAxis == Z) Triple(0.8f, 0.8f, 1f) else Triple(0.2f, 0.2f, 1f)
                drawWorldLineWithOffset(
                    worldPoseMatrix,
                    gizmoBuffer,
                    gizmoPos,
                    gizmoZEndWorld,
                    zAxisColor.first,
                    zAxisColor.second,
                    zAxisColor.third,
                    gizmoAlpha
                )
            }

            // 如果有选中的轴和偏移量，绘制偏移后的位置
            if (selectedAxis != null && axisWorldCoordinates != null && axisDebugOffset != null) {
                // 计算偏移后的位置
                val offsetPos = Vector3f(axisWorldCoordinates).add(axisDebugOffset)

                // 绘制偏移后的位置标记
                drawDebugPoint(worldPoseMatrix, gizmoBuffer, offsetPos, 0.05f, 1f, 1f, 0f, 1f) // 黄色标记

                // 绘制从原始位置到偏移位置的虚线
                drawWorldLineWithOffset(
                    worldPoseMatrix, gizmoBuffer,
                    axisWorldCoordinates!!, offsetPos, 1f, 1f, 0f, 0.5f
                ) // 半透明黄色虚线

                // 在偏移位置绘制坐标轴
                val offsetAxisLength = 0.2f // 偏移位置的坐标轴长度更短
                val offsetXEnd = Vector3f(offsetPos).add(offsetAxisLength, 0f, 0f)
                val offsetYEnd = Vector3f(offsetPos).add(0f, offsetAxisLength, 0f)
                val offsetZEnd = Vector3f(offsetPos).add(0f, 0f, offsetAxisLength)

                drawWorldLineWithOffset(worldPoseMatrix, gizmoBuffer, offsetPos, offsetXEnd, 1f, 0.5f, 0.5f, gizmoAlpha)
                drawWorldLineWithOffset(worldPoseMatrix, gizmoBuffer, offsetPos, offsetYEnd, 0.5f, 1f, 0.5f, gizmoAlpha)
                drawWorldLineWithOffset(worldPoseMatrix, gizmoBuffer, offsetPos, offsetZEnd, 0.5f, 0.5f, 1f, gizmoAlpha)
            }

            // 在IK模式下，绘制持久化的IK目标和末端执行器位置
            if (ikModeEnabled && selectedIKChain != null) {
                // 绘制持久化的IK目标位置
                if (persistentIKTargetPosition != null) {
                    // 绘制IK目标位置标记（紫色）
                    drawDebugPoint(
                        worldPoseMatrix,
                        gizmoBuffer,
                        persistentIKTargetPosition!!,
                        0.07f,
                        0.8f,
                        0.2f,
                        1f,
                        1f
                    ) // 紫色标记

                    // 在IK目标位置绘制坐标轴
                    val targetAxisLength = 0.25f
                    val targetXEnd = Vector3f(persistentIKTargetPosition).add(targetAxisLength, 0f, 0f)
                    val targetYEnd = Vector3f(persistentIKTargetPosition).add(0f, targetAxisLength, 0f)
                    val targetZEnd = Vector3f(persistentIKTargetPosition).add(0f, 0f, targetAxisLength)

                    // 如果正在编辑IK目标坐标轴，使用更亮的颜色
                    val xAxisColor = if (editingIKTarget && ikTargetAxisSelected == X) Triple(1f, 0.5f, 1f) else Triple(
                        1f,
                        0.3f,
                        0.8f
                    )
                    val yAxisColor = if (editingIKTarget && ikTargetAxisSelected == Y) Triple(0.5f, 1f, 1f) else Triple(
                        0.3f,
                        1f,
                        0.8f
                    )
                    val zAxisColor =
                        if (editingIKTarget && ikTargetAxisSelected == Z) Triple(0.5f, 0.5f, 1f) else Triple(
                            0.3f,
                            0.3f,
                            1f
                        )

                    drawWorldLineWithOffset(
                        worldPoseMatrix,
                        gizmoBuffer,
                        persistentIKTargetPosition!!,
                        targetXEnd,
                        xAxisColor.first,
                        xAxisColor.second,
                        xAxisColor.third,
                        gizmoAlpha
                    ) // X轴
                    drawWorldLineWithOffset(
                        worldPoseMatrix,
                        gizmoBuffer,
                        persistentIKTargetPosition!!,
                        targetYEnd,
                        yAxisColor.first,
                        yAxisColor.second,
                        yAxisColor.third,
                        gizmoAlpha
                    ) // Y轴
                    drawWorldLineWithOffset(
                        worldPoseMatrix,
                        gizmoBuffer,
                        persistentIKTargetPosition!!,
                        targetZEnd,
                        zAxisColor.first,
                        zAxisColor.second,
                        zAxisColor.third,
                        gizmoAlpha
                    ) // Z轴

                    // 添加“IK目标”文本标记
                    val targetTextPos = Vector3f(persistentIKTargetPosition).add(0f, 0.3f, 0f)
                    // drawWorldText(worldPoseMatrix, "IK目标", targetTextPos, 0.8f, 0.2f, 1f, 1f)
                }

                // 绘制持久化的末端执行器实际位置
                if (persistentEndEffectorPosition != null) {
                    // 绘制末端执行器实际位置标记（青色）
                    drawDebugPoint(
                        worldPoseMatrix,
                        gizmoBuffer,
                        persistentEndEffectorPosition!!,
                        0.07f,
                        0.2f,
                        0.8f,
                        1f,
                        1f
                    ) // 青色标记

                    // 如果同时有IK目标位置，绘制从末端执行器到IK目标的连线
                    if (persistentIKTargetPosition != null) {
                        drawWorldLineWithOffset(
                            worldPoseMatrix, gizmoBuffer,
                            persistentEndEffectorPosition!!, persistentIKTargetPosition!!, 0.5f, 0.5f, 1f, 0.7f
                        ) // 半透明蓝紫色连线
                    }

                    // 添加“实际位置”文本标记
                    val effectorTextPos = Vector3f(persistentEndEffectorPosition).add(0f, 0.3f, 0f)
                    // drawWorldText(worldPoseMatrix, "实际位置", effectorTextPos, 0.2f, 0.8f, 1f, 1f)
                }
            }

            // 结束线段批处理
            bufferSource.endBatch(RenderType.lines())

            // 重新绘制文本标记（在结束线段批处理后）
            if (ikModeEnabled && selectedIKChain != null) {
                if (persistentIKTargetPosition != null) {
                    val targetTextPos = Vector3f(persistentIKTargetPosition).add(0f, 0.3f, 0f)
                    drawWorldText(worldPoseMatrix, "IK目标", targetTextPos, 0.8f, 0.2f, 1f, 1f)
                }

                if (persistentEndEffectorPosition != null) {
                    val effectorTextPos = Vector3f(persistentEndEffectorPosition).add(0f, 0.3f, 0f)
                    drawWorldText(worldPoseMatrix, "实际位置", effectorTextPos, 0.2f, 0.8f, 1f, 1f)
                }
            }
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

            // 已经在上面结束了线段批处理

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
    private fun collectDescendantCubeVertices(
        bone: OBone,
        animatable: IAnimatable<*>,
        partialTick: Float
    ): List<List<Vector3f>> {
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
    private fun drawWorldLine(
        poseMatrix: Matrix4f,
        buffer: VertexConsumer,
        start: Vector3f,
        end: Vector3f,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        buffer.addVertex(poseMatrix, start.x, start.y, start.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, end.x, end.y, end.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
    }

    // --- 当两个面或线在同一深度时，会出现闪烁 ---
    private fun drawWorldLineWithOffset(
        poseMatrix: Matrix4f,
        buffer: VertexConsumer,
        start: Vector3f,
        end: Vector3f,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
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
        buffer.addVertex(poseMatrix, startOffset.x, startOffset.y, startOffset.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, endOffset.x, endOffset.y, endOffset.z).setColor(r, g, b, a).setNormal(0f, 1f, 0f)
    }

    // --- Helper to draw a debug point (small octahedron) ---
    private fun drawDebugPoint(
        poseMatrix: Matrix4f,
        buffer: VertexConsumer,
        position: Vector3f,
        size: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val halfSize = size / 2f

        // 绘制八面体的八个三角形
        // 直接绘制线段而不是三角形

        // 上半部分
        // 第一个三角形
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 第二个三角形
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 第三个三角形
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 第四个三角形
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y + halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 下半部分
        // 第五个三角形
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 第六个三角形
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z + halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 第七个三角形
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x - halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        // 第八个三角形
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x + halfSize, position.y, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)

        buffer.addVertex(poseMatrix, position.x, position.y, position.z - halfSize).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
        buffer.addVertex(poseMatrix, position.x, position.y - halfSize, position.z).setColor(r, g, b, a)
            .setNormal(0f, 1f, 0f)
    }

    // --- IK Mode Handling ---
    private fun toggleIKMode() {
        ikModeEnabled = !ikModeEnabled

        // 切换动画列表和IK链列表的显示
        animationSelectionList.visible = !ikModeEnabled
        ikChainSelectionList.visible = ikModeEnabled

        if (ikModeEnabled) {
            // 进入IK模式
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player ?: return

            // 检查玩家是否是IKHost
            if (player is IEntityAnimatable<*>) {
                // 获取可用的IK链
                ikChains = player.ikManager.activeComponents
                ikChainSelectionList.updateIKChains(ikChains)

                // 重置选择状态
                selectedIKChain = null
                selectedIKBones.clear()
                endEffectorBone = null

                SparkCore.LOGGER.debug("IK模式已启用，可用IK链: {}", ikChains.keys)
            } else {
                SparkCore.LOGGER.debug("Player不是IKHost，无法启用IK模式")
                ikModeEnabled = false
                animationSelectionList.visible = true
                ikChainSelectionList.visible = false
            }
        } else {
            // 退出IK模式
            selectedIKChain = null
            selectedIKBones.clear()
            endEffectorBone = null

            // 重置持久化显示的IK目标和末端执行器位置
            persistentIKTargetPosition = null
            persistentEndEffectorPosition = null

            // 恢复正常选择状态
            selectedBone = null
            selectedCube = null
            selectedAxis = null
            axisWorldCoordinates = null
            axisDebugOffset = null

            SparkCore.LOGGER.debug("IK模式已关闭")
        }
    }

    private fun selectIKChain(chainName: String) {
        selectedIKChain = chainName
        selectedIKBones.clear()

        // 重置持久化显示的IK目标和末端执行器位置
        persistentIKTargetPosition = null
        persistentEndEffectorPosition = null

        // 重置轴选择状态
        selectedAxis = null
        axisWorldCoordinates = null
        axisDebugOffset = null
        draggingAxis = null

        val component = ikChains[chainName] ?: return
        val chain = component.chain

        // 获取链中的骨骼
        val boneNames = mutableListOf<String>()

        // 使用IKComponentType中的信息获取骨骼名称
        if (component.type.bonePathNames != null) {
            boneNames.addAll(component.type.bonePathNames)
        } else {
            // 如果没有明确的路径，使用起始和结束骨骼
            boneNames.add(component.type.startBoneName)
            boneNames.add(component.type.endBoneName)
        }

        // 获取骨骼对象
        for (boneName in boneNames) {
            model?.bones?.get(boneName)?.let { bone ->
                selectedIKBones.add(bone)
            }
        }

        // 设置末端执行器骨骼
        endEffectorBone = model?.bones?.get(component.targetBoneName)

        // 更新TreeView选择
        endEffectorBone?.let { bone ->
            treeView.setSelectedElement(bone)
        }

        SparkCore.LOGGER.debug("Selected IK chain: {}, bones: {}", chainName, selectedIKBones.map { it.name })
    }

    // --- Animation Handling for Model Switch --- M
    private fun updateAndPlayIdleAnimationForModel(animatable: IAnimatable<*>, targetModelLocation: ResourceLocation) {
        // 收集目标模型的所有相关动画
        currentAnimations = collectAllAnimationsForModel(targetModelLocation)
        animationSelectionList.updateAnimations(currentAnimations)

        // Reset scroll for the animation list
        animationSelectionList.resetScroll()

        SparkCore.LOGGER.debug("Updated animation list for model $targetModelLocation: ${currentAnimations.size} animations found")

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

        try {
            // 需要找到包含这个动画的正确动画集合
            val animIndex = findAnimationIndex(animationName)
            if (animIndex != null) {
                val animInstance = AnimInstance.create(animatable, animIndex)
                animatable.animController.getMainLayer().setAnimation(animInstance)
                SparkCore.LOGGER.debug("Successfully playing animation: $animationName from ${animIndex}")
            } else {
                SparkCore.LOGGER.error("Could not find animation index for: $animationName")
                minecraft?.gui?.chat?.addMessage(Component.literal("Animation not found: $animationName"))
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("Failed to play animation '$animationName'", e)
            minecraft?.gui?.chat?.addMessage(Component.literal("Error playing animation: ${e.message}"))
        }
    }

    // --- Screen Lifecycle Methods ---
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
        SparkCore.LOGGER.debug("WebBrowserWidget closed.")


        // 如果在IK模式下，重置目标位置
        if (ikModeEnabled && selectedIKChain != null) {
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player
            val animatable = player as? IAnimatable<*> ?: return

            // 重置持久化显示的IK目标和末端执行器位置
            persistentIKTargetPosition = null
            persistentEndEffectorPosition = null

            // Find and play idle animation
            val idleAnim = currentAnimations.firstOrNull { it.contains("idle", ignoreCase = true) }
            if (idleAnim != null) {
                playAnimation(idleAnim) // Use the common play function
            } else if (currentAnimations.isNotEmpty()) {
                playAnimation(currentAnimations[0]) // Play first if no idle found
            } else {
                animatable.stopAllAnimations()
            }
        }

        super.onClose()
    }

    // --- Helper to collect all animations for a model --- M
    private fun collectAllAnimationsForModel(modelLocation: ResourceLocation): List<String> {
        val allAnimations = mutableListOf<String>()
        val pathParts = modelLocation.path.split("/")
        if (pathParts.size >= 3 && pathParts[1] == "models") {
            val moduleName = pathParts[0]
            val entityName = pathParts.last()

            // 遍历所有已注册的动画集合，找到匹配的
            OAnimationSet.ORIGINS.forEach { (key, animSet) ->
                if (key.namespace == modelLocation.namespace &&
                    key.path.startsWith("$moduleName/animations/$entityName/")
                ) {
                    animSet.animations.forEach { (name, anim) ->
                        allAnimations.add(name)
                    }
                    SparkCore.LOGGER.debug("Found animation set: $key with ${animSet.animations.size} animations")
                }
            }
        }
        SparkCore.LOGGER.debug(
            "Collected {} animations for model {}: {}",
            allAnimations.size,
            modelLocation,
            allAnimations
        )
        return allAnimations.distinct()
    }


    // --- Helper to find the correct AnimIndex for an animation name --- M
    private fun findAnimationIndex(animationName: String): AnimIndex? {
        val animation = SparkRegistries.TYPED_ANIMATION.get(ResourceLocation.parse(buildAnimationPathFromModel(modelLocation).toString() + "/" + normalizeResourceName(animationName)))
        return if (animation != null) {
            animation.index
        } else {
            SparkCore.LOGGER.warn("Could not find animation '$animationName' for model '${modelLocation.path}'")
            null
        }
    }

}


/**
 * 计算射线到线段的最短距离，并返回最近点
 */
private fun rayDistanceToLineSegment(
    rayOrigin: Vector3f,
    rayDirection: Vector3f,
    lineStart: Vector3f,
    lineEnd: Vector3f
): Pair<Float, Vector3f> {
    // 线段向量
    val lineVec = Vector3f(lineEnd).sub(lineStart)

    // 射线方向和线段方向的叉积
    val cross = Vector3f(rayDirection).cross(lineVec)
    val crossLengthSq = cross.lengthSquared()

    // 如果叉积接近于零，说明射线和线段平行
    if (crossLengthSq < 0.0001f) {
        // 计算射线原点到线段起点的距离
        val distToStart = Vector3f(lineStart).sub(rayOrigin).length()
        return Pair(distToStart, Vector3f(lineStart))
    }

    // 计算射线原点到线段所在直线的距离
    val originToLineStart = Vector3f(lineStart).sub(rayOrigin)
    val perpendicular = originToLineStart.dot(cross) / cross.length()

    // 计算射线和线段所在直线的交点参数
    val t = Vector3f(originToLineStart).cross(lineVec).length() / cross.length()
    val s = Vector3f(originToLineStart).cross(rayDirection).length() / cross.length()

    // 检查交点是否在线段上
    val lineLength = lineVec.length()
    if (s >= 0 && s <= lineLength) {
        // 交点在线段上，计算交点坐标
        val pointOnLine = Vector3f(lineStart).add(Vector3f(lineVec).mul(s / lineLength))
        return Pair(Math.abs(perpendicular), pointOnLine)
    }

    // 交点不在线段上，计算到线段端点的距离
    val distToStart = Vector3f(lineStart).sub(rayOrigin).cross(rayDirection).length() / rayDirection.length()
    val distToEnd = Vector3f(lineEnd).sub(rayOrigin).cross(rayDirection).length() / rayDirection.length()

    return if (distToStart < distToEnd) {
        Pair(distToStart, Vector3f(lineStart))
    } else {
        Pair(distToEnd, Vector3f(lineEnd))
    }
}


// --- IAnimatable Extension Placeholder --- M
// Assuming IAnimatable has methods like these based on JS extensions
// If not, these need to be adapted or implemented in IAnimatable interface/implementations
fun IAnimatable<*>.stopAllAnimations() {
    // Setting the animation to null on the controller should stop the current one
    try {
        this.animController.animLayers.values.forEach { it.setAnimation(null) } // Use 0 transition time to stop immediately
    } catch (e: Exception) {
        SparkCore.LOGGER.error("Failed to stop animations", e)
    }
}

// --- Helper to draw text in 3D world space ---
private fun drawWorldText(
    poseMatrix: Matrix4f,
    text: String,
    position: Vector3f,
    r: Float,
    g: Float,
    b: Float,
    a: Float
) {
    val minecraft = Minecraft.getInstance()
    val camera = minecraft.gameRenderer.mainCamera

    // 获取相机位置和方向
    val cameraPos = camera.position
    val cameraDir = Vector3f(
        (position.x - cameraPos.x).toFloat(),
        (position.y - cameraPos.y).toFloat(),
        (position.z - cameraPos.z).toFloat()
    ).normalize()

    // 计算旋转角度，使文本面向相机
    val yaw = Math.toDegrees(atan2(cameraDir.x.toDouble(), cameraDir.z.toDouble())).toFloat()
    val pitch = Math.toDegrees(asin(cameraDir.y.toDouble())).toFloat()

    // 创建变换矩阵
    val matrixStack = PoseStack()
    matrixStack.pushPose()

    // 设置位置
    matrixStack.translate(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())

    // 旋转使文本面向相机
    matrixStack.mulPose(Axis.YP.rotationDegrees(-yaw))
    matrixStack.mulPose(Axis.XP.rotationDegrees(pitch))

    // 缩放文本大小
    val scale = 0.02f // 调整文本大小
    matrixStack.scale(-scale, -scale, scale) // 负值使文本正面朝向相机

    // 获取渲染缓冲区
    val bufferSource = minecraft.renderBuffers().bufferSource()

    // 绘制文本
    val font = minecraft.font
    val textWidth = font.width(text)
    val color =
        ((a * 255).toInt() shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    font.drawInBatch(
        text,
        -textWidth / 2f,
        0f,
        color,
        false,
        matrixStack.last().pose(),
        bufferSource,
        Font.DisplayMode.NORMAL,
        0,
        15728880
    )

    // 结束批处理
    bufferSource.endBatch()

    // 恢复矩阵状态
    matrixStack.popPose()
}

