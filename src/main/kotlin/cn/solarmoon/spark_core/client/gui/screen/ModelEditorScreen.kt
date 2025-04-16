package cn.solarmoon.spark_core.client.gui.screen

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.client.gui.screen.ModelEditorScreen.Axis.*
import cn.solarmoon.spark_core.client.gui.widget.ModelTreeViewWidget
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import java.util.LinkedList
import java.io.File // For Save button
import com.google.gson.GsonBuilder // Assuming Gson is used for JSON serialization
import net.minecraft.client.gui.components.Button
import org.lwjgl.glfw.GLFW // Needed for key codes
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.Util // For Util.getMillis()
import cn.solarmoon.spark_core.registry.client.SparkKeyMappings // Import key mappings
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.VertexFormat
import kotlin.div
import kotlin.text.clear
import kotlin.text.get

class ModelEditorScreen(private val modelLocation: ResourceLocation, private val textureLocation: ResourceLocation) : Screen(Component.translatable("spark_core.screen.model_editor.title")) {

    // --- 渲染目标 (FBO) ---
    private var sceneRenderTarget: RenderTarget? = null
    private var viewportWidth: Int = 300 // FBO 尺寸，需要根据 UI 调整
    private var viewportHeight: Int = 300

    // --- 场景相机 ---
    private val sceneCamera = Camera()
    private var sceneCameraPos: Vec3 = Vec3.ZERO // 保存计算好的相机位置
    private var sceneCameraDist: Float = 3.0f // 相机距离目标的距离
    private var sceneCameraYaw: Float = 0f    // 水平旋转 (绕 Y 轴)
    private var sceneCameraPitch: Float = 15f   // 垂直旋转 (绕 X 轴)

    // --- 目标实体 ---
    private var targetEntity: Zombie? = null // 目标实体，需要初始化

    // --- TreeView ---
    private lateinit var treeView: ModelTreeViewWidget
    private var selectedBone: OBone? = null
    private var selectedCube: OCube? = null
    private var model: OModel? = null // 仍然需要 OModel 来构建 TreeView

    // --- Gizmo 交互状态 ---
    private var draggingAxis: Axis? = null // null, X, Y, or Z
    private var dragStartX: Double = 0.0
    private var dragStartY: Double = 0.0
    // 可能还需要存储拖拽开始时的元素初始变换或 pivot

    // --- 缓存的 Gizmo 屏幕坐标 (在 renderGizmoAndHighlightOnUI 中更新) ---
    private var gizmoPivotScreen: Vector3f? = null
    private var gizmoXEndScreen: Vector3f? = null
    private var gizmoYEndScreen: Vector3f? = null
    private var gizmoZEndScreen: Vector3f? = null

    // --- Undo/Redo History ---
    private val undoStack = LinkedList<EditAction>()
    private val redoStack = LinkedList<EditAction>()
    // --- Model Selection List ---
    private lateinit var modelSelectionList: ModelSelectionList
    private var originalPivotBeforeDrag: Vec3? = null // Store original pivot when starting drag

    private enum class Axis { X, Y, Z }
    // --- Model Selection List Widget ---
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
    override fun init() {
        super.init()
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (level == null) {
            minecraft.player?.sendSystemMessage(Component.literal("Error: Client level not loaded!"))
            this.onClose()
            return
        }

        // --- 1. 初始化或获取目标实体 ---
        // TODO: 更可靠地获取或创建实体。暂时尝试在玩家附近创建一个
        // 这只是临时方案，需要一个安全、不影响游戏的方式
        targetEntity = EntityType.ZOMBIE.create(level)
        if (targetEntity == null) {
             minecraft.player?.sendSystemMessage(Component.literal("Error: Could not create target entity!"))
             this.onClose()
             return
        }
        // 设置一个远离玩家的位置，避免干扰
        val playerPos = minecraft.player?.position() ?: Vec3.ZERO
        targetEntity!!.setPos(playerPos.x + 10, playerPos.y, playerPos.z) // 放在玩家旁边
        // (可选) 将实体添加到世界？如果是临时预览，可能不需要
        // level.addFreshEntity(targetEntity!!) // 可能导致服务器问题或可见性问题

        // --- 2. 修改实体的 ModelIndex ---
        val animatable = targetEntity as? IAnimatable<*> // 确保 Zombie 实现了 IAnimatable
        if (animatable == null) {
            minecraft.player?.sendSystemMessage(Component.literal("Error: Target entity is not IAnimatable!"))
            this.onClose()
            return
        }
        // TODO: 确保 modelIndex 是可变的
        try {
            animatable.modelIndex = ModelIndex(modelLocation, textureLocation)
        } catch (e: Exception) {
             minecraft.player?.sendSystemMessage(Component.literal("Error: Failed to set ModelIndex: ${e.message}"))
             // 可能 modelIndex 是 val? 需要检查 IAnimatable 实现
             this.onClose()
             return
        }

        // --- 3. 加载 OModel (用于 TreeView) ---
        // 即使渲染实体，TreeView 仍需要 OModel 结构
        model = OModel.ORIGINS[modelLocation]
        if (model == null) {
            minecraft.player?.sendSystemMessage(Component.literal("Error: Model not found in ORIGINS: $modelLocation"))
            this.onClose()
            return
        }


        // --- 4. 初始化 TreeView (使用 OModel) ---
        val treeViewWidth = 150
        val treeViewHeight = this.height - 40
        val treeViewX = this.width - treeViewWidth - 10
        val treeViewY = 20

        // 实例化新的 TreeView，传入 model
        treeView = ModelTreeViewWidget(treeViewX, treeViewY, treeViewWidth, treeViewHeight, model) { selectedElement ->
            // 更新选择状态
            selectedBone = null
            selectedCube = null

            when (selectedElement) {
                is OBone -> {
                    selectedBone = selectedElement
                    println("Selected Bone: ${selectedElement.name}")
                }
                is OCube -> {
                    // 从 Cube 获取父骨骼需要 TreeView 内部传递信息，或者在这里查找
                    // 暂时无法直接从 OCube 获取父 OBone，需要调整 TreeView 回调或存储父子关系
                    selectedCube = selectedElement
                    // 查找父骨骼 (效率较低，最好由 TreeView 提供)
                    selectedBone = model?.bones?.values?.find { it.cubes.contains(selectedElement) }
                    println("Selected Cube (Parent: ${selectedBone?.name ?: "Unknown"})")
                }
            }
        }

        // 设置根节点
        val rootNodes = model!!.bones.values
            .filter { it.parentName == null || model!!.bones[it.parentName] == null }
        // 不再需要 map 到 BoneNode
        treeView.setNodes(rootNodes)
        this.addRenderableWidget(treeView)

        // --- 5. 创建 RenderTarget (FBO) ---
        // 确定视口尺寸
        viewportWidth = (this.width * 0.7f).toInt() - 30 // 假设视口占屏幕宽度70%，减去边距和 TreeView 宽度
        viewportHeight = this.height - 40 // 假设视口占满高度，减去上下边距

        // 确保 FBO 在渲染线程创建 (init 应该是在主线程)
        RenderSystem.recordRenderCall {
            if (sceneRenderTarget == null || sceneRenderTarget!!.width != viewportWidth || sceneRenderTarget!!.height != viewportHeight) {
                sceneRenderTarget?.destroyBuffers() // 清理旧的
                sceneRenderTarget = TextureTarget(viewportWidth, viewportHeight, true, Minecraft.ON_OSX) // 使用 TextureTarget
                sceneRenderTarget!!.setClearColor(0f, 0f, 0f, 0f) // Optional: Set clear color (transparent background)
                 try {
                    sceneRenderTarget!!.createBuffers(viewportWidth, viewportHeight, Minecraft.ON_OSX)
                 } catch (e: Exception) {
                     SparkCore.LOGGER.error("Failed to create ModelEditor RenderTarget", e)
                     sceneRenderTarget = null // Mark as failed
                     // Optionally close screen or show error message
                 }
            }
        }

        // --- 6. 注册渲染事件监听器 ---
        NeoForge.EVENT_BUS.register(this)

        // --- 7. 添加 Undo/Redo/Save 按钮 ---
        val buttonY = this.height - 25 // Position buttons at the bottom
        val buttonWidth = 50
        val buttonHeight = 20
        var currentButtonX = 10

        // Undo Button
        addRenderableWidget(Button.builder(Component.literal("Undo")) { _ ->
            if (undoStack.isNotEmpty()) {
                val action = undoStack.pop()
                action.undo(treeView) // Pass treeView for refresh
                redoStack.push(action)
                println("Undo performed. Undo: ${undoStack.size}, Redo: ${redoStack.size}") // Debug
            }
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // Redo Button
        addRenderableWidget(Button.builder(Component.literal("Redo")) { _ ->
            if (redoStack.isNotEmpty()) {
                val action = redoStack.pop()
                action.redo(treeView) // Pass treeView for refresh
                undoStack.push(action)
                 println("Redo performed. Undo: ${undoStack.size}, Redo: ${redoStack.size}") // Debug
            }
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())
        currentButtonX += buttonWidth + 5

        // Save Button
        addRenderableWidget(Button.builder(Component.literal("Save")) { _ ->
            saveModelToFile()
        }.bounds(currentButtonX, buttonY, buttonWidth, buttonHeight).build())


        // --- 8. 初始化模型选择列表 ---
        val listWidth = treeViewWidth // Match TreeView width for alignment
        val listHeight = this.height - treeViewY - buttonHeight - 10 // Fill space below TreeView, above buttons
        val listX = treeViewX
        val listY = treeViewY + treeViewHeight + 5 // Position below TreeView with some padding

        modelSelectionList = ModelSelectionList(minecraft, listWidth, listHeight, listY, font.lineHeight + 3)
        this.addRenderableWidget(modelSelectionList)


        // --- 9. 初始化相机位置 (首次) ---
        updateSceneCameraPosition()

    }

    override fun onClose() {
        // --- 清理 FBO 和注销监听器 ---
        NeoForge.EVENT_BUS.unregister(this)
        val targetToDestroy = sceneRenderTarget // Capture reference
        sceneRenderTarget = null // Clear field
        if (targetToDestroy != null) {
            RenderSystem.recordRenderCall {
                targetToDestroy.destroyBuffers()
            }
        }
        // Remove the temporary entity if it exists
        targetEntity?.discard()
        super.onClose()
    }

    // 更新场景相机位置和朝向 (基于距离、偏航、俯仰)
    private fun updateSceneCameraPosition() {
        if (targetEntity == null) return

        // 目标中心点 (近似使用实体位置)
        val targetPos = targetEntity!!.position() //.add(0.0, targetEntity!!.eyeHeight / 2.0, 0.0) // Optional: Center on entity height

        // 计算相机位置 (球坐标 -> 笛卡尔坐标)
        val yawRad = Math.toRadians(sceneCameraYaw.toDouble())
        val pitchRad = Math.toRadians(sceneCameraPitch.toDouble())

        val xOffset = -Math.sin(yawRad) * Math.cos(pitchRad) * sceneCameraDist
        val yOffset = -Math.sin(pitchRad) * sceneCameraDist
        val zOffset = -Math.cos(yawRad) * Math.cos(pitchRad) * sceneCameraDist

        // 我们只需要更新相机参数，实际上在 onRenderLevelStage 中会重新计算视图矩阵
        // 不再尝试直接修改 Camera 对象（因为那些方法是 protected 的）
        // 保留计算好的相机参数供后续使用
        sceneCameraPos = Vec3(
            targetPos.x + xOffset, 
            targetPos.y + yOffset, 
            targetPos.z + zOffset
        )
    }


    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // --- 1. 渲染背景和 TreeView ---
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        // TreeView 会被 super.render 调用
        super.render(guiGraphics, mouseX, mouseY, partialTick) // Render widgets

        // --- 2. 绘制 FBO 纹理到 UI ---
        if (sceneRenderTarget?.getColorTextureId() != -1 && sceneRenderTarget != null) {
            val textureId = sceneRenderTarget!!.getColorTextureId()
            val viewportX = 10 // UI 中 FBO 视口的左上角 X
            val viewportY = 20 // UI 中 FBO 视口的左上角 Y

            RenderSystem.setShader(GameRenderer::getPositionTexShader)
            RenderSystem.enableBlend() // Needed for transparency if FBO has alpha
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            // 手动使用 Tesselator 绘制 FBO 纹理
            RenderSystem.setShaderTexture(0, textureId) // 绑定 FBO 纹理
            val tesselator = Tesselator.getInstance()
            val bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

            val x1 = viewportX.toFloat()
            val y1 = viewportY.toFloat()
            val x2 = (viewportX + viewportWidth).toFloat()
            val y2 = (viewportY + viewportHeight).toFloat()
            val z = 0f // UI 平面

            // UV 坐标 (V 坐标可能需要翻转，取决于 FBO 实现, 0,0 在左下角)
            val u0 = 0f
            val v0 = 0f // FBO 纹理底部
            val u1 = 1f
            val v1 = 1f // FBO 纹理顶部

            val matrix = guiGraphics.pose().last().pose()
            bufferBuilder.addVertex(matrix, x1, y2, z).setUv(u0, v0)
            bufferBuilder.addVertex(matrix, x2, y2, z).setUv(u1, v0)
            bufferBuilder.addVertex(matrix, x2, y1, z).setUv(u1, v1)
            bufferBuilder.addVertex(matrix, x1, y1, z).setUv(u0, v1)

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())

            RenderSystem.disableBlend() // Restore blend state

            // --- 3. 绘制 Gizmo/Highlight (在 FBO 纹理之上) ---
             if (selectedBone != null || selectedCube != null) {
                  renderGizmoAndHighlightOnUI(guiGraphics, partialTick)
             }

        } else {
            // FBO 未就绪时显示提示信息
            guiGraphics.drawCenteredString(this.font, "Initializing Render Target...", this.width / 2, this.height / 2, 0xFFFFFF.toInt())
        }
    }

    // --- 事件监听器，用于注入渲染 ---
    @SubscribeEvent
    fun onRenderLevelStage(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_LEVEL || targetEntity == null || sceneRenderTarget == null || Minecraft.getInstance().screen != this) {
            // 只在正确的阶段、FBO 和实体有效、且当前屏幕是编辑器时执行
            return
        }
        if (sceneRenderTarget!!.getColorTextureId() == -1) return // FBO 未完全创建

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val dispatcher = minecraft.entityRenderDispatcher
        val partialTicks = event.partialTick.getGameTimeDeltaPartialTick(true)

        // --- 1. 备份渲染状态 ---
        val mainRenderTarget = minecraft.mainRenderTarget
        val originalProjection = Matrix4f(RenderSystem.getProjectionMatrix()) // 备份投影矩阵 (copy)
        val originalModelView = Matrix4f(RenderSystem.getModelViewMatrix())   // 备份模型视图矩阵 (copy)
        // Backup other relevant states
        val oldFogColor = RenderSystem.getShaderFogColor().clone() // Clone to avoid modification
        val oldFogStart = RenderSystem.getShaderFogStart()
        val oldFogEnd = RenderSystem.getShaderFogEnd()
        // val oldShader = RenderSystem.getShader() // Backing up shader might be complex/unnecessary if entity renderer resets it

        RenderSystem.recordRenderCall { // 确保在渲染线程执行 GL 操作
            // --- 2. 绑定 FBO ---
            sceneRenderTarget!!.bindWrite(true)
            sceneRenderTarget!!.clear(Minecraft.ON_OSX) // 清空 FBO

            // --- 3. 设置场景相机矩阵 ---
            // 计算视图矩阵 (直接从 sceneCameraPos, sceneCameraYaw, sceneCameraPitch 计算，不使用 sceneCamera 对象)
            val viewRotationMatrix = Matrix4f().rotateY(Math.toRadians((sceneCameraYaw + 180).toDouble()).toFloat())
                                             .rotateX(Math.toRadians(-sceneCameraPitch.toDouble()).toFloat())
            val sceneViewMatrix = Matrix4f(viewRotationMatrix)
            sceneViewMatrix.translate(-sceneCameraPos.x.toFloat(), -sceneCameraPos.y.toFloat(), -sceneCameraPos.z.toFloat())

            // 计算投影矩阵 (基于 FBO 尺寸和固定 FOV)
            val fov = 70.0
            val aspectRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
            val nearPlane = 0.05f
            val farPlane = minecraft.gameRenderer.depthFar // 使用游戏默认远平面
            val sceneProjectionMatrix = Matrix4f().setPerspective(
                Math.toRadians(fov).toFloat(), aspectRatio, nearPlane, farPlane
            )

            // 设置渲染系统矩阵
            RenderSystem.setProjectionMatrix(sceneProjectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN)
             val mvStack = RenderSystem.getModelViewStack()
             mvStack.identity() // 重置模型视图栈
             mvStack.mul(sceneViewMatrix) // 应用视图矩阵
             RenderSystem.applyModelViewMatrix() // 应用到 RenderSystem

            // --- 4. 渲染实体到 FBO ---
            // 实体渲染需要相对于相机的位置，由于视图矩阵已应用，理论上实体应该在世界坐标原点被渲染？
            // 或者，渲染器内部会处理偏移？
            // EntityRenderer.render 通常需要 (entity, x, y, z, yaw, partialTicks, poseStack, bufferSource, light)
            // 其中的 x,y,z 是世界坐标。PoseStack 应该只包含模型本身的局部变换（如果需要）
            // 但我们的 ModelView 矩阵已经包含了世界到视图的变换。
            // 尝试直接传入 0,0,0 渲染，让 ModelView 矩阵处理坐标
             val light = LevelRenderer.getLightColor(level, targetEntity!!.blockPosition())
             val renderPoseStack = PoseStack() // 为渲染实体创建一个新的 PoseStack

            // Setup render state for FBO (optional, defaults might be fine)
            // RenderSystem.enableBlend() // Ensure blend is enabled if needed
            // RenderSystem.defaultBlendFunc()
            RenderSystem.enableDepthTest() // Ensure depth test is enabled
            // RenderSystem.depthFunc(GL11.GL_LEQUAL) // Default depth func
            RenderSystem.enableCull() // Ensure culling is enabled
            // RenderSystem.cullFace(GL11.GL_BACK) // Default cull face

            // 禁用 FOG，避免影响 FBO 渲染 (Set fog far away)
            RenderSystem.setShaderFogColor(0f, 0f, 0f, 0f) // Set to clear color or disable influence
            RenderSystem.setShaderFogStart(farPlane * 2) // Move fog far away
            RenderSystem.setShaderFogEnd(farPlane * 3)

            try {
                 dispatcher.render(targetEntity!!, 0.0, 0.0, 0.0, targetEntity!!.getYRot(), partialTicks, renderPoseStack, bufferSource, light)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("Error rendering target entity to FBO", e)
            } finally {
                 // FOG is restored later in the main restore block
            }


            bufferSource.endBatch() // 确保实体渲染提交

            // --- 5. 恢复状态 ---
            mainRenderTarget.bindWrite(true) // 切回主 FBO
            RenderSystem.setProjectionMatrix(originalProjection, VertexSorting.DISTANCE_TO_ORIGIN) // 恢复投影矩阵
             mvStack.identity() // 重置模型视图栈
             mvStack.mul(originalModelView) // 恢复模型视图矩阵 (这样对吗？还是直接设置 RenderSystem.modelViewMatrix?)
             RenderSystem.applyModelViewMatrix()

            // --- 6. 恢复之前备份的状态 ---
            // Restore Fog
            RenderSystem.setShaderFogColor(oldFogColor[0], oldFogColor[1], oldFogColor[2], oldFogColor[3])
            RenderSystem.setShaderFogStart(oldFogStart)
            RenderSystem.setShaderFogEnd(oldFogEnd)
            // Restore Shader (if backed up)
            // RenderSystem.setShader { oldShader }

        }
    }

    // --- Gizmo/Highlight 绘制 (新方法) ---
    private fun renderGizmoAndHighlightOnUI(guiGraphics: GuiGraphics, partialTick: Float) {
         if (targetEntity == null || model == null) return
         val minecraft = Minecraft.getInstance()

         // --- 1. 获取场景相机的投影和视图矩阵 ---
         // (需要重新计算或缓存 onRenderLevelStage 中的矩阵)
         // 注意：这些矩阵将世界坐标变换到 [-1, 1] 的裁剪空间 (Clip Space)
         val fov = 70.0; val aspectRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
         val nearPlane = 0.05f; val farPlane = minecraft.gameRenderer.depthFar
         val sceneProjectionMatrix = Matrix4f().setPerspective(Math.toRadians(fov).toFloat(), aspectRatio, nearPlane, farPlane)
         val viewRotationMatrix = Matrix4f().rotateY(Math.toRadians((sceneCameraYaw + 180).toDouble()).toFloat())
                                        .rotateX(Math.toRadians(-sceneCameraPitch.toDouble()).toFloat())
         val sceneViewMatrix = Matrix4f(viewRotationMatrix)
                             .translate(-sceneCameraPos.x.toFloat(), -sceneCameraPos.y.toFloat(), -sceneCameraPos.z.toFloat())
         val viewProjectionMatrix = Matrix4f(sceneProjectionMatrix).mul(sceneViewMatrix)

         // --- 2. 获取要绘制元素的世界坐标 ---
         val elementWorldMatrix: Matrix4f? = if (selectedCube != null && selectedBone != null) {
             // 优先处理选中的 Cube (它属于 selectedBone)
              // 需要计算方块的世界矩阵: 父骨骼世界矩阵 * 方块局部变换
              val parentBoneMatrix = targetEntity!!.getWorldBoneMatrix(selectedBone!!.name, partialTick)
              val cubeLocalMatrix = Matrix4f()
              cubeLocalMatrix.translate(selectedCube!!.pivot.toVector3f())
              cubeLocalMatrix.rotateZYX(selectedCube!!.rotation.toVector3f())
              cubeLocalMatrix.translate(selectedCube!!.pivot.toVector3f().negate())
              Matrix4f(parentBoneMatrix).mul(cubeLocalMatrix)
         } else if (selectedBone != null) {
             // 如果只选中了 Bone
             targetEntity!!.getWorldBoneMatrix(selectedBone!!.name, partialTick)
         } else {
             null
         }
         if (elementWorldMatrix == null) {
             // 清除 Gizmo 缓存如果没选中任何东西
             gizmoPivotScreen = null; gizmoXEndScreen = null; gizmoYEndScreen = null; gizmoZEndScreen = null
             return
         }


         // --- 3. 将世界坐标变换到屏幕坐标 (UI 视口内) ---
         val viewportX = 10f
         val viewportY = 20f
         val viewportW = viewportWidth.toFloat()
         val viewportH = viewportHeight.toFloat()

         // Helper function to project world point to UI viewport coordinates
         fun projectToViewport(worldPos: Vector3f): Vector3f? {
             val clipSpacePos = viewProjectionMatrix.transform(Vector4f(worldPos, 1.0f))
             if (clipSpacePos.w <= 0) return null // Behind camera

             // Perspective divide -> Normalized Device Coordinates (NDC) [-1, 1]
             val ndc = Vector3f(clipSpacePos.x / clipSpacePos.w, clipSpacePos.y / clipSpacePos.w, clipSpacePos.z / clipSpacePos.w)

             // Check if within NDC bounds (optional, helps culling lines outside view)
             // 稍微扩大一点边界检查，防止严格边界导致线条消失
             if (ndc.x < -1.1f || ndc.x > 1.1f || ndc.y < -1.1f || ndc.y > 1.1f) return null

             // NDC to Viewport coordinates
             val screenX = viewportX + (ndc.x + 1.0f) * 0.5f * viewportW
             val screenY = viewportY + (1.0f - ndc.y) * 0.5f * viewportH // Y is inverted in screen space
             val screenZ = ndc.z // Keep Z for potential depth sorting if needed
             return Vector3f(screenX, screenY, screenZ)
         }

         // --- 4. 绘制 Gizmo ---
         if (selectedBone != null || selectedCube != null) {
             val pivotWorldPos = elementWorldMatrix.transformPosition(Vector3f(0f, 0f, 0f))
             // 更新缓存的屏幕坐标
             gizmoPivotScreen = projectToViewport(pivotWorldPos)
             if (gizmoPivotScreen == null) {
                 // 如果轴心点不在屏幕上，清除所有缓存并退出Gizmo绘制
                 gizmoXEndScreen = null; gizmoYEndScreen = null; gizmoZEndScreen = null
                 return
             }
             val pivotScreenPos = gizmoPivotScreen!! // Not null here

             val axisLengthWorld = 0.3f // Gizmo length in world space
             val worldXDir = Vector3f(); elementWorldMatrix.getColumn(0, worldXDir).normalize().mul(axisLengthWorld)
             val worldYDir = Vector3f(); elementWorldMatrix.getColumn(1, worldYDir).normalize().mul(axisLengthWorld)
             val worldZDir = Vector3f(); elementWorldMatrix.getColumn(2, worldZDir).normalize().mul(axisLengthWorld)

             // 更新缓存
             gizmoXEndScreen = projectToViewport(Vector3f(pivotWorldPos).add(worldXDir))
             gizmoYEndScreen = projectToViewport(Vector3f(pivotWorldPos).add(worldYDir))
             gizmoZEndScreen = projectToViewport(Vector3f(pivotWorldPos).add(worldZDir))

             // 使用缓存或实时计算的值进行绘制
             val xEndScreen = gizmoXEndScreen
             val yEndScreen = gizmoYEndScreen
             val zEndScreen = gizmoZEndScreen

             val bufferSourceUI = minecraft.renderBuffers().bufferSource()
             val lineBufferUI = bufferSourceUI.getBuffer(RenderType.guiOverlay()) // Use GUI overlay type for no depth test

             val poseUI = guiGraphics.pose().last().pose()
             val normalUI = guiGraphics.pose().last().normal() // Needed by format

             // 只绘制在屏幕内的轴
             if (xEndScreen != null) drawScreenLine(poseUI, normalUI, lineBufferUI, pivotScreenPos, xEndScreen, 1f, 0f, 0f, 1f)
             if (yEndScreen != null) drawScreenLine(poseUI, normalUI, lineBufferUI, pivotScreenPos, yEndScreen, 0f, 1f, 0f, 1f)
             if (zEndScreen != null) drawScreenLine(poseUI, normalUI, lineBufferUI, pivotScreenPos, zEndScreen, 0f, 0f, 1f, 1f)

             bufferSourceUI.endBatch(RenderType.guiOverlay())
         } else {
             // 如果没有选中，清除Gizmo缓存
             gizmoPivotScreen = null; gizmoXEndScreen = null; gizmoYEndScreen = null; gizmoZEndScreen = null
         }

         // --- 5. 绘制 Highlight ---
          if (selectedCube != null) {
              val o = selectedCube!!.originPos.toVector3f()
              val s = selectedCube!!.size.toVector3f()
              // 定义 8 个局部顶点 (相对于方块自身的 originPos)
              val lv = listOf(
                  Vector3f(o.x, o.y, o.z),                 // 0: --- (min, min, min)
                  Vector3f(o.x + s.x, o.y, o.z),         // 1: +-- (max, min, min)
                  Vector3f(o.x + s.x, o.y, o.z + s.z), // 2: ++- (max, min, max)
                  Vector3f(o.x, o.y, o.z + s.z),         // 3: -+- (min, min, max)
                  Vector3f(o.x, o.y + s.y, o.z),         // 4: --+ (min, max, min)
                  Vector3f(o.x + s.x, o.y + s.y, o.z), // 5: +-+ (max, max, min)
                  Vector3f(o.x + s.x, o.y + s.y, o.z + s.z), // 6: +++ (max, max, max)
                  Vector3f(o.x, o.y + s.y, o.z + s.z)  // 7: -++ (min, max, max)
              )
              val wv = lv.map { elementWorldMatrix.transformPosition(it, Vector3f()) }
              val sv = wv.mapNotNull { projectToViewport(it) } // Project world vertices to screen

              // 仅当所有顶点都在屏幕内时才绘制 (简化处理，避免跨屏幕线条)
              if (sv.size == 8) {
                  val bufferSourceUI = minecraft.renderBuffers().bufferSource()
                  val lineBufferUI = bufferSourceUI.getBuffer(RenderType.guiOverlay())
                  val poseUI = guiGraphics.pose().last().pose()
                  val r=0.2f; val g=0.6f; val b=1f; val a=1f

                  // 传递 normalMatrix (尽管在 drawScreenLine 中目前未使用)
                  val normalUI = guiGraphics.pose().last().normal()
                  // 绘制 12 条边
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[0], sv[1], r, g, b, a) // Bottom
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[1], sv[2], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[2], sv[3], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[3], sv[0], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[4], sv[5], r, g, b, a) // Top
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[5], sv[6], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[6], sv[7], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[7], sv[4], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[0], sv[4], r, g, b, a) // Verticals
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[1], sv[5], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[2], sv[6], r, g, b, a)
                  drawScreenLine(poseUI, normalUI, lineBufferUI, sv[3], sv[7], r, g, b, a)

                  bufferSourceUI.endBatch(RenderType.guiOverlay())
              }
          }
    }

     // Helper to draw lines directly in screen space for UI overlay
     private fun drawScreenLine(matrix: Matrix4f, normalMatrix: Matrix3f, buffer: VertexConsumer, start: Vector3f, end: Vector3f, r: Float, g: Float, b: Float, a: Float) {
         val normal = Vector3f(0f, 0f, 1f) // Simple Z normal for UI lines
         buffer.addVertex(matrix, start.x, start.y, start.z).setColor(r, g, b, a).setNormal(normal.x, normal.y, normal.z)
         buffer.addVertex(matrix, end.x, end.y, end.z).setColor(r, g, b, a).setNormal(normal.x, normal.y, normal.z)
     }


    // --- 相机控制 ---
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        // 鼠标是否在 FBO 视口内?
        val viewportX = 10; val viewportY = 20
         if (mouseX >= viewportX && mouseX < viewportX + viewportWidth &&
             mouseY >= viewportY && mouseY < viewportY + viewportHeight)
         {
             if (button == 0) { // 左键拖动
                 // 如果正在拖拽 Gizmo，则处理 Gizmo 拖拽逻辑
                 if (draggingAxis != null) {
                     // TODO: 实现 Gizmo 拖拽导致的元素移动
                     handleGizmoDrag(mouseX, mouseY, dragX, dragY)
                     return true
                 } else { // 否则旋转场景相机
                     sceneCameraYaw -= dragX.toFloat() * 0.5f // 水平拖动 -> 偏航
                     sceneCameraPitch += dragY.toFloat() * 0.5f // 垂直拖动 -> 俯仰
                     sceneCameraPitch = sceneCameraPitch.coerceIn(-89.9f, 89.9f) // 限制俯仰角
                     updateSceneCameraPosition()
                     return true
                 }
             }
             // TODO: 右键拖动或其他按钮可以用来平移相机?
         } else if (treeView.isMouseOver(mouseX, mouseY)) {
              // 如果在 TreeView 上，让 TreeView 处理
             return treeView.mouseDragged(mouseX, mouseY, button, dragX, dragY)
         }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    // 处理 Gizmo 拖拽的核心逻辑
    private fun handleGizmoDrag(mouseX: Double, mouseY: Double, dragX: Double, dragY: Double) {
        if (draggingAxis == null || gizmoPivotScreen == null) return

        // 1. 获取屏幕上的轴方向
        val axisStart = gizmoPivotScreen!!
        val axisEnd: Vector3f? = when(draggingAxis) {
            X -> gizmoXEndScreen
            Y -> gizmoYEndScreen
            Z -> gizmoZEndScreen
            null -> TODO()
        }
        if (axisEnd == null) return // 轴的端点不在屏幕上

        // 2. 计算屏幕轴方向向量 (归一化)
        val screenAxisDir = Vector3f(axisEnd).sub(axisStart)
        if (screenAxisDir.lengthSquared() < 1e-6f) return // 屏幕上轴长度过短，无法判断方向
        screenAxisDir.normalize()

        // 3. 计算鼠标屏幕移动向量
        val mouseDelta = Vector3f(dragX.toFloat(), dragY.toFloat(), 0f)

        // 4. 将鼠标移动投影到屏幕轴方向 (点乘)
        val projection = mouseDelta.dot(screenAxisDir) // 这是鼠标在屏幕轴方向上移动的像素距离

        // 5. 将屏幕像素距离转换为世界距离
        // 使用 Gizmo 渲染时的世界长度和其在屏幕上的投影长度进行比例换算
        val screenAxisLength = axisStart.distance(axisEnd)
        if (screenAxisLength < 0.1f) return // 屏幕投影过短，比例无意义

        val worldAxisLength = 0.3f // 与 Gizmo 渲染时使用的长度一致
        val worldDisplacementMagnitude = projection * (worldAxisLength / screenAxisLength)

        // 6. 获取世界轴向
        val partialTick = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(true) // 需要 partialTick 获取最新矩阵
        val elementWorldMatrix = getElementWorldMatrix(partialTick) ?: return
        val worldAxisDir = Vector3f()
        when (draggingAxis) {
            X -> elementWorldMatrix.getColumn(0, worldAxisDir)
            Y -> elementWorldMatrix.getColumn(1, worldAxisDir)
            Z -> elementWorldMatrix.getColumn(2, worldAxisDir)
            null -> TODO()
        }
        if (worldAxisDir.lengthSquared() < 1e-6f) return // 世界轴方向无效？
        worldAxisDir.normalize()

        // 7. 计算最终的世界位移向量
        val worldDisplacement = Vector3f(worldAxisDir).mul(worldDisplacementMagnitude)

        // 8. 应用位移到模型数据
        applyWorldDisplacement(worldDisplacement)

        // 更新调试信息
        // println("Screen Proj: $projection, World Disp: $worldDisplacementMagnitude, Axis: $draggingAxis")
    }

    // 辅助函数获取当前选中元素的世界矩阵
    private fun getElementWorldMatrix(partialTick: Float): Matrix4f? {
         if (targetEntity == null) return null
         return if (selectedCube != null && selectedBone != null) {
             val parentBoneMatrix = targetEntity!!.getWorldBoneMatrix(selectedBone!!.name, partialTick)
             val cubeLocalMatrix = Matrix4f()
             cubeLocalMatrix.translate(selectedCube!!.pivot.toVector3f())
             cubeLocalMatrix.rotateZYX(selectedCube!!.rotation.toVector3f())
             cubeLocalMatrix.translate(selectedCube!!.pivot.toVector3f().negate())
             Matrix4f(parentBoneMatrix).mul(cubeLocalMatrix)
         } else if (selectedBone != null) {
             targetEntity!!.getWorldBoneMatrix(selectedBone!!.name, partialTick)
         } else {
             null
         }
    }

     // TODO: 实现将世界位移应用到本地 pivot 的逻辑
     private fun applyWorldDisplacement(worldDisplacement: Vector3f) {
         if (targetEntity == null || model == null) return
         val partialTick = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(true)
         val animatable = targetEntity as? IAnimatable<*> ?: return

         if (selectedCube != null && selectedBone != null) {
             // --- 修改 Cube 的 Pivot ---
             // 1. 获取父骨骼的世界矩阵
             //    注意：getWorldBoneMatrix 通常包含骨骼自身的变换，我们需要的是 *不* 包含 selectedBone 自身变换的矩阵
             //    所以调用 getParentWorldMatrix 获取父级的矩阵
             val parentWorldMatrix = getParentWorldMatrix(selectedBone!!.name, animatable, partialTick) ?: Matrix4f() // 根骨骼父级为实体世界变换或 Identity

             // 2. 计算父骨骼的逆矩阵，将世界位移方向转到父骨骼的本地空间
             val invParentWorldMatrix = Matrix4f(parentWorldMatrix).invert()
             val localDisplacement = invParentWorldMatrix.transformDirection(worldDisplacement, Vector3f())
             if (localDisplacement.lengthSquared() < 1e-9f) return // 位移过小，忽略

             // 3. 计算新的 Pivot (Vec3 是 Double)
             val oldPivot = selectedCube!!.pivot
             val newPivot = Vec3(
                 oldPivot.x + localDisplacement.x.toDouble(),
                 oldPivot.y + localDisplacement.y.toDouble(),
                 oldPivot.z + localDisplacement.z.toDouble()
             )

             // 4. 创建新的 Cube 实例并替换旧的 (因为 pivot 是 val)
             val newCube = selectedCube!!.copy(pivot = newPivot)
             val cubeIndex = selectedBone!!.cubes.indexOf(selectedCube)
             if (cubeIndex != -1) {
                 selectedBone!!.cubes[cubeIndex] = newCube
                 selectedCube = newCube // 更新屏幕的选择引用
                 // 可能需要通知 TreeView 更新显示？
                 println("Moved Cube to Pivot: $newPivot")
             } else {
                 println("Error: Could not find selected cube in parent bone's list.")
             }

         } else if (selectedBone != null) {
             // --- 修改 Bone 的 Pivot ---
             // 1. 获取父骨骼的世界矩阵
             val parentWorldMatrix = getParentWorldMatrix(selectedBone!!.name, animatable, partialTick) ?: Matrix4f()

             // 2. 计算逆矩阵，转换世界位移方向到父骨骼本地空间
             val invParentWorldMatrix = Matrix4f(parentWorldMatrix).invert()
             val localDisplacement = invParentWorldMatrix.transformDirection(worldDisplacement, Vector3f())
             if (localDisplacement.lengthSquared() < 1e-9f) return // 位移过小

             // 3. 计算新的 Pivot
             val oldPivot = selectedBone!!.pivot
             val newPivot = Vec3(
                 oldPivot.x + localDisplacement.x.toDouble(),
                 oldPivot.y + localDisplacement.y.toDouble(),
                 oldPivot.z + localDisplacement.z.toDouble()
             )

             // 4. 创建新的 Bone 实例并替换旧的
             val newBone = selectedBone!!.copy(pivot = newPivot)
             model!!.bones[selectedBone!!.name] = newBone // 替换 model 中的引用
             selectedBone = newBone // 更新屏幕的选择引用
             // TODO: 需要更新 TreeView Widget 中的节点数据
             println("Moved Bone ${selectedBone!!.name} to Pivot: $newPivot")
         }
     }
    // --- Model Switching Logic ---
    private fun switchModel(newModelLocation: ResourceLocation) {
        println("Switching model to: $newModelLocation")

        // 1. Get the new OModel from cache
        val newOModel = OModel.ORIGINS[newModelLocation]
        if (newOModel == null) {
            minecraft?.player?.sendSystemMessage(Component.literal("Error: Could not find model $newModelLocation in ORIGINS cache."))
            return
        }

        // 2. Infer textureLocation (CRITICAL ASSUMPTION - needs verification/adjustment)
        val newTextureLocation = try {
            val modelPath = newModelLocation.path
            val texturePath = when {
                modelPath.startsWith("geo/model/") -> modelPath.replaceFirst("geo/model/", "textures/entity/")
                else -> "textures/entity/$modelPath" // Assume root model path maps to textures/entity/
            }
            // Ensure .png extension (or handle other formats if necessary)
            val texturePathPng = if (texturePath.endsWith(".png")) texturePath else "$texturePath.png"
            ResourceLocation.fromNamespaceAndPath(newModelLocation.namespace, texturePathPng)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("Failed to infer texture location for model $newModelLocation", e)
            this.textureLocation // Fallback to original texture if inference fails
        }
        println("Inferred texture location: $newTextureLocation")

        // 3. Update internal model reference (for TreeView, Save, EditAction)
        this.model = newOModel
        // Note: We don't update this.modelLocation or this.textureLocation as they are constructor vals.
        // Save function uses this.model, and entity update uses new locations.

        // 4. Update target entity's model index (This triggers the renderer to use the new model/texture)
        val animatable = targetEntity as? IAnimatable<*>
        if (animatable != null) {
            try {
                animatable.modelIndex = ModelIndex(newModelLocation, newTextureLocation)
            } catch (e: Exception) {
                minecraft?.player?.sendSystemMessage(Component.literal("Error setting model index on entity: ${e.message}"))
            }
        } else {
            minecraft?.player?.sendSystemMessage(Component.literal("Error: Target entity is not IAnimatable."))
        }

        // 5. Update TreeView
        val rootNodes = model!!.bones.values
            .filter { it.parentName == null || model!!.bones[it.parentName] == null }
        treeView.setNodes(rootNodes)

        // 6. Clear Undo/Redo stacks
        undoStack.clear()
        redoStack.clear()

        // 7. Reset selection
        selectedBone = null
        selectedCube = null

        // Update screen title? (Optional)
        // this.title = Component.translatable("spark_core.screen.model_editor.title", newModelLocation.path)

        println("Model switched successfully.")
    }

      // 辅助函数：获取指定骨骼的父骨骼的世界矩阵
      // (如果骨骼是根骨骼，返回实体的世界变换或 Identity)
      private fun getParentWorldMatrix(boneName: String, animatable: IAnimatable<*>, partialTick: Float): Matrix4f? {
          if (model == null) return null
          val bone = model!!.bones[boneName] ?: return null
          val parentName = bone.parentName
          return if (parentName != null && model!!.bones.containsKey(parentName)) {
              // 递归或迭代获取父骨骼矩阵
              animatable.getWorldBoneMatrix(parentName, partialTick)
          } else {
              // 假定 getWorldBoneMatrix(root) 已经包含了实体变换，那么根骨骼的"父"就是世界原点 (Identity)
              // 否则，需要计算实体的世界变换
              Matrix4f().translate(animatable.getWorldPosition(partialTick).toVector3f())
                         .rotateY(animatable.getRootYRot(partialTick))
              // 或者简单返回 Identity Matrix4f()，取决于 getWorldBoneMatrix 是否已包含实体变换
              // Matrix4f() // Identity seems safer if getBoneWorldMatrix includes entity transform
          }
      }


    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // 鼠标是否在 FBO 视口内?
        val viewportX = 10; val viewportY = 20
        if (mouseX >= viewportX && mouseX < viewportX + viewportWidth &&
            mouseY >= viewportY && mouseY < viewportY + viewportHeight)
        {
             sceneCameraDist -= (scrollY * 0.2f * sceneCameraDist).toFloat() // 滚轮调整相机距离
             sceneCameraDist = sceneCameraDist.coerceIn(0.5f, 20f) // 限制距离
             updateSceneCameraPosition()
             return true
        } else if (treeView.isMouseOver(mouseX, mouseY)) {
             return treeView.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
         }
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    // 其他 Screen 方法 (isPauseScreen etc.) 保持不变
     override fun isPauseScreen(): Boolean = false

     // TODO: 添加处理 TreeView 点击的逻辑 (mouseClicked 需要转发给 TreeView)
     override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
         // 优先让 TreeView 处理点击
         if (treeView.mouseClicked(mouseX, mouseY, button)) {
             return true
         }

        // 处理 Gizmo 点击检测
        val viewportX = 10; val viewportY = 20
        if (button == 0 && draggingAxis == null && // 仅左键且未开始拖拽
            mouseX >= viewportX && mouseX < viewportX + viewportWidth &&
            mouseY >= viewportY && mouseY < viewportY + viewportHeight &&
            gizmoPivotScreen != null) // 确保 Gizmo 渲染过且可见
        {
            val mouseXf = mouseX.toFloat()
            val mouseYf = mouseY.toFloat()
            val thresholdSqr = 5f * 5f // 点击阈值（像素平方）

            var minDistSqr = Float.MAX_VALUE
            var nearestAxis: Axis? = null

            // 检测 X 轴
            if (gizmoXEndScreen != null) {
                val dist = distToSegmentSqr(mouseXf, mouseYf, gizmoPivotScreen!!.x, gizmoPivotScreen!!.y, gizmoXEndScreen!!.x, gizmoXEndScreen!!.y)
                if (dist < minDistSqr) {
                    minDistSqr = dist
                    nearestAxis = Axis.X
                }
            }
            // 检测 Y 轴
            if (gizmoYEndScreen != null) {
                val dist = distToSegmentSqr(mouseXf, mouseYf, gizmoPivotScreen!!.x, gizmoPivotScreen!!.y, gizmoYEndScreen!!.x, gizmoYEndScreen!!.y)
                if (dist < minDistSqr) {
                    minDistSqr = dist
                    nearestAxis = Axis.Y
                }
            }
            // 检测 Z 轴
            if (gizmoZEndScreen != null) {
                val dist = distToSegmentSqr(mouseXf, mouseYf, gizmoPivotScreen!!.x, gizmoPivotScreen!!.y, gizmoZEndScreen!!.x, gizmoZEndScreen!!.y)
                if (dist < minDistSqr) {
                    minDistSqr = dist
                    nearestAxis = Axis.Z
                }
            }

            // 如果足够近，开始拖拽
            if (minDistSqr < thresholdSqr && nearestAxis != null) {
                draggingAxis = nearestAxis
                dragStartX = mouseX
                dragStartY = mouseY
                 println("Dragging Gizmo Axis: $nearestAxis") // 调试信息
                // Record original pivot before starting drag
                val targetElement = selectedCube ?: selectedBone
                if (targetElement != null) {
                    originalPivotBeforeDrag = when (targetElement) {
                        is OBone -> targetElement.pivot
                        is OCube -> targetElement.pivot
                        else -> null // Should not happen if selection logic is correct
                    }
                    println("Started dragging $nearestAxis, original pivot: $originalPivotBeforeDrag") // Debug
                } else {
                    originalPivotBeforeDrag = null // Ensure it's null if nothing relevant is selected
                }
                return true // 事件已处理
            }
        }

         return super.mouseClicked(mouseX, mouseY, button)
     }
    // --- Save Functionality ---
    fun saveModelToFile() {
        if (model == null) {
            minecraft?.player?.sendSystemMessage(Component.literal("Error: No model data loaded to save."))
            return
        }

        try {
            // 1. Construct the target file path
            val basePath = "F:/Work/code/test/Spark-Core/build/resources/main/data/"
            val namespace = modelLocation.namespace
            val path = modelLocation.path // e.g., "geo/model/my_model" or just "my_model"
            // Ensure the path includes the necessary subdirectory if not already present
            val modelPath = if (path.startsWith("geo/model/")) path else "geo/model/$path"
            val fullPath = "$basePath$namespace/$modelPath.json"
            val targetFile = File(fullPath)

            // Ensure parent directories exist
            targetFile.parentFile?.mkdirs()

            // 2. Serialize the model to JSON (using Gson)
            // Ensure Gson dependency is available and configured for OModel structure
            val gson = GsonBuilder().setPrettyPrinting().create() // Use pretty printing for readability
            val jsonString = gson.toJson(model)

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

    // --- Keyboard Input Handling ---
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Check for Undo (Ctrl+Z)
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

        // Let other widgets handle key presses if needed (e.g., text fields if added later)
        // return super.keyPressed(keyCode, scanCode, modifiers)

        return false // Indicate key was not handled here if not Undo/Redo/Esc
    }

     override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
         if (button == 0 && draggingAxis != null) {
             println("Stopped dragging Gizmo Axis: $draggingAxis") // 调试信息
             val axisJustDragged = draggingAxis
             draggingAxis = null

             // Create EditAction if pivot changed
             val targetElement = selectedCube ?: selectedBone // Get the element that was dragged
             if (targetElement != null && originalPivotBeforeDrag != null) {
                 val currentPivot = when (targetElement) {
                     is OBone -> targetElement.pivot
                     is OCube -> targetElement.pivot
                     else -> null
                 }

                 // Check if pivot actually changed (using a small tolerance for floating point comparison might be better)
                 if (currentPivot != null && currentPivot != originalPivotBeforeDrag) {
                      val action = PivotEditAction(targetElement, originalPivotBeforeDrag!!, currentPivot, model)
                      undoStack.push(action)
                      redoStack.clear() // Clear redo stack on new action
                      println("Pivot changed. Added action to undo stack. Undo: ${undoStack.size}, Redo: ${redoStack.size}") // Debug
                      // TreeView refresh should happen after applying the change (in applyWorldDisplacement or undo/redo)
                      // But we might need one here too if applyWorldDisplacement doesn't cover all cases or for immediate visual feedback
                      treeView.refreshVisibleNodes()
                 } else {
                      println("Pivot did not change significantly.") // Debug
                 }
             }
             originalPivotBeforeDrag = null // Reset after drag ends

             println("Stopped dragging Gizmo Axis: $axisJustDragged") // 调试信息
             // TODO: 可能需要最终确认修改或记录历史 (Partially addressed by Undo stack)
             return true
         }
         return super.mouseReleased(mouseX, mouseY, button)
     }

// --- Edit Action for Undo/Redo ---
sealed interface EditAction {
    // Pass treeView instance for refreshing after action
    fun undo(treeView: ModelTreeViewWidget)
    fun redo(treeView: ModelTreeViewWidget)
}

data class PivotEditAction(
    val targetElement: Any, // OBone or OCube
    val oldPivot: Vec3,
    val newPivot: Vec3,
    val modelRef: OModel? // Reference to the model for modification
) : EditAction {

    private fun setPivot(pivot: Vec3) {
        when (targetElement) {
            is OBone -> {
                val currentBone = modelRef?.bones?.get(targetElement.name)
                if (currentBone != null) {
                    val updatedBone = currentBone.copy(pivot = pivot)
                    modelRef?.bones?.put(targetElement.name, updatedBone)
                    // Update the reference in the screen if it's the selected one
                    // This requires passing ModelEditorScreen instance or a callback
                    // For now, assume direct model modification is enough, TreeView refresh handles UI
                } else {
                    SparkCore.LOGGER.warn("Cannot find bone '${targetElement.name}' in model during pivot update.")
                }
            }
            is OCube -> {
                // Find the parent bone first
                val parentBone = modelRef?.bones?.values?.find { it.cubes.contains(targetElement) }
                if (parentBone != null) {
                    val cubeIndex = parentBone.cubes.indexOf(targetElement)
                    if (cubeIndex != -1) {
                        val updatedCube = targetElement.copy(pivot = pivot)
                        parentBone.cubes[cubeIndex] = updatedCube
                        // Update the reference in the screen if it's the selected one
                    } else {
                         SparkCore.LOGGER.warn("Cannot find cube index in parent bone '${parentBone.name}' during pivot update.")
                    }
                } else {
                    SparkCore.LOGGER.warn("Cannot find parent bone for cube during pivot update.")
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
}

// 需要添加 OModel.EMPTY (在 OModel.kt)
// 检查 OModel.kt 是否已有 EMPTY 实例
// object OModel {
//      val ORIGINS = mutableMapOf<ResourceLocation, OModel>()
//      val EMPTY = OModel(0, 0, LinkedHashMap()) // 添加一个空的模型实例
//      // ... (其他 OModel 内容)
// }


// 计算点 P 到线段 AB 最短距离的平方
private fun distToSegmentSqr(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val l2 = distSqr(ax, ay, bx, by)
    if (l2 == 0f) return distSqr(px, py, ax, ay) // 线段是一个点
    // 计算投影点 t = dot(P-A, B-A) / |B-A|^2
    val t = ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / l2
    val clampedT = t.coerceIn(0f, 1f) // 将 t 限制在线段范围内 [0, 1]
    // 找到线段上离 P 最近的点 projection
    val projX = ax + clampedT * (bx - ax)
    val projY = ay + clampedT * (by - ay)
    return distSqr(px, py, projX, projY) // 返回点 P 到投影点的距离平方
}

// 计算两点距离的平方
private fun distSqr(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
}