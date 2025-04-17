package cn.solarmoon.spark_core.client.gui.screen

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.client.gui.widget.ModelTreeViewWidget
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings
import com.google.gson.GsonBuilder
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

class ModelEditorScreen(private val modelLocation: ResourceLocation, private val textureLocation: ResourceLocation) : Screen(Component.translatable("spark_core.screen.model_editor.title")) {

    // --- Main Camera Control State (used by CameraMixin) ---
    var cameraDistance: Float = 5.0f // Renamed from sceneCameraDist
        private set // Allow external read for Mixin, but modification only via methods
    var cameraYaw: Float = 0f // Renamed from sceneCameraYaw
        private set
    var cameraPitch: Float = 15f // Renamed from sceneCameraPitch
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
        val availableHeight = this.height - topMargin - bottomMargin - padding

        val sidebarWidth = 150 // From backup

        val modelListHeight = (availableHeight-padding)/2 // From backup (listHeight)

        // Right Sidebar Coordinates (TreeView & Model List)

        val modelListY =topMargin + padding + availableHeight/2 // From backup (listY)

        // Left Column Coordinates (Animation List)
        val animListX = sideMargin
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
            }
         // Check if dragging ModelSelectionList
         if (modelSelectionList.isMouseOver(mouseX, mouseY)) {
             if (modelSelectionList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                  return true
             }
         }
         // Check if dragging AnimationSelectionList
        if (animationSelectionList.isMouseOver(mouseX, mouseY)) {
             if (animationSelectionList.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true
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
        // Check if scrolling over TreeView first
        if (treeView.isMouseOver(mouseX, mouseY)) {
            if (treeView.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
        }
        // Check if scrolling over ModelSelectionList
        if (modelSelectionList.isMouseOver(mouseX, mouseY)) {
            if (modelSelectionList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
        }
        // --- Add check for AnimationSelectionList --- M
        if (animationSelectionList.isMouseOver(mouseX, mouseY)) {
            if (animationSelectionList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true
            }
        }

        // If not scrolling over UI, adjust camera distance (zoom)
        // Adjust sensitivity/direction as needed
        val zoomAmount = scrollY * 0.1f * cameraDistance // Make scroll speed dependent on current distance
        cameraDistance -= zoomAmount.toFloat()
        cameraDistance = cameraDistance.coerceIn(0.5f, 50f) // Adjust limits as needed
        // No need to call update method, Mixin reads state directly
        return true
    }

    // isPauseScreen remains false
    override fun isPauseScreen(): Boolean = false

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Prioritize widgets
        if (treeView.mouseClicked(mouseX, mouseY, button)) {
            return true
        }
        if (modelSelectionList.mouseClicked(mouseX, mouseY, button)) {
            return true
        }
        // --- Animation List Click --- M
        if (animationSelectionList.mouseClicked(mouseX, mouseY, button)) {
            return true
        }

        // --- Gizmo Click Detection (NEEDS REWORK) ---
        // This logic requires recalculating gizmo screen positions based on main camera
        // and world-to-screen projection within GUI context.
        /*
        val viewportX = 10; val viewportY = 20 // Example UI area, needs adjustment
        if (button == 0 && draggingAxis == null && // Left click, not already dragging
            // Check if click is within the main 'viewport' area (excluding sidebars)
            mouseX >= viewportX && mouseX < (this.width - 150 - 20) && // Adjust based on sidebar layout
            mouseY >= viewportY && mouseY < (this.height - 30) &&      // Adjust based on button layout
            (selectedBone != null || selectedCube != null) // Something must be selected
           )
        {
             // TODO: Recalculate Gizmo screen coords (gizmoPivotScreen, etc.) based on main camera
             // Example: Use Camera.project or manual matrix math

             // Placeholder check - assuming coords were recalculated
             if (gizmoPivotScreen != null) {
            val mouseXf = mouseX.toFloat()
            val mouseYf = mouseY.toFloat()
                val thresholdSqr = 7f * 7f // Click threshold

            var minDistSqr = Float.MAX_VALUE
            var nearestAxis: Axis? = null

                // Simplified check (Needs proper projection and distToSegmentSqr)
                if (gizmoXEndScreen != null && gizmoPivotScreen!!.distanceSquared(mouseXf, mouseYf, 0f) < thresholdSqr) nearestAxis = Axis.X
                if (gizmoYEndScreen != null && gizmoPivotScreen!!.distanceSquared(mouseXf, mouseYf, 0f) < thresholdSqr) nearestAxis = Axis.Y // Placeholder logic
                if (gizmoZEndScreen != null && gizmoPivotScreen!!.distanceSquared(mouseXf, mouseYf, 0f) < thresholdSqr) nearestAxis = Axis.Z // Placeholder logic


                if (nearestAxis != null) {
                draggingAxis = nearestAxis
                dragStartX = mouseX
                dragStartY = mouseY
                    println("Dragging Gizmo Axis: $nearestAxis")

                    // Record original pivot (Use Player/IAnimatable)
                    val player = Minecraft.getInstance().player as? IAnimatable<*>
                val targetElement = selectedCube ?: selectedBone
                    if (targetElement != null && player != null) {
                         // This part relies on PivotEditAction being updated too
                    originalPivotBeforeDrag = when (targetElement) {
                             is OBone -> targetElement.pivot // Assuming OModel holds the *base* pivot
                        is OCube -> targetElement.pivot
                             else -> null
                    }
                         println("Started dragging $nearestAxis, original pivot: $originalPivotBeforeDrag")
                } else {
                        originalPivotBeforeDrag = null
                }
                    return true // Event handled
            }
        }
        */

        // Fallback to super if not handled by widgets or Gizmo click
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
        // ... (Undo/Redo logic is unchanged)
        if (SparkKeyMappings.MODEL_EDITOR_UNDO.matches(keyCode, scanCode)) {
            if (undoStack.isNotEmpty()) {
                val action = undoStack.pop()
                action.undo(treeView) // Pass treeView for refresh
                redoStack.push(action)
                println("Undo performed via shortcut. Undo: ${undoStack.size}, Redo: ${redoStack.size}") // Debug
                return true // Key handled
            }
        }

        // Check for Redo (Ctrl+Y)
        if (SparkKeyMappings.MODEL_EDITOR_REDO.matches(keyCode, scanCode)) {
            if (redoStack.isNotEmpty()) {
                val action = redoStack.pop()
                action.redo(treeView) // Pass treeView for refresh
                undoStack.push(action)
                println("Redo performed via shortcut. Undo: ${undoStack.size}, Redo: ${redoStack.size}") // Debug
                return true // Key handled
            }
        }
        // Allow Esc key to close the screen (default behavior)
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true
        }
        return false
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
        val pivotWorldPos = animatable.getWorldBonePivot(gizmoTargetBoneName ?: "") ?: return // Gizmo needs a bone pivot

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
        val boneWorldMatrix = animatable.getWorldBoneMatrix(bone.name, partialTick) ?: return emptyList() // Stop if bone matrix fails

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

