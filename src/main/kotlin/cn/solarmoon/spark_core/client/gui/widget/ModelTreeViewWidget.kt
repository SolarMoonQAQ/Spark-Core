package cn.solarmoon.spark_core.client.gui.widget

import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OModel
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractScrollWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.max

class ModelTreeViewWidget(
    x: Int, y: Int, width: Int, height: Int,
    private var model: OModel?, // 需要持有 OModel 的引用来查找子骨骼
    private var itemHeight: Int = 12,
    private var indentation: Int = 10,
    private val onSelectionChanged: (Any?) -> Unit // 回调传递 OBone 或 OCube
) : AbstractScrollWidget(x, y, width, height, Component.empty()) {
    val MENU_LIST_BACKGROUND: ResourceLocation =
        ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png")
    val INWORLD_MENU_LIST_BACKGROUND: ResourceLocation =
        ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png")


    private val minecraft: Minecraft = Minecraft.getInstance()
    private val font: Font = minecraft.font
    private var rootBones: List<OBone> = emptyList()
    private var flatVisibleNodes: MutableList<Pair<Any, Int>> = mutableListOf() // Pair<OBone | OCube, Level>
    private var expandedBones: MutableSet<String> = mutableSetOf() // 存储展开的骨骼名称
    private var selectedElement: Any? = null // OBone or OCube
    private var contentHeight = 0

    init {
        // 默认展开所有根骨骼
        rootBones.forEach { expandedBones.add(it.name) }
    }

    fun setNodes(rootNodes: List<OBone>, model: OModel) {
        this.model = model
        this.rootBones = rootNodes
        this.expandedBones.clear()
        // 默认展开根骨骼
        rootNodes.forEach { expandedBones.add(it.name) }
        refreshVisibleNodes()
        setScrollAmount(0.0)
    }
    override fun renderBackground(guiGraphics: GuiGraphics) {
        // Draw a semi-transparent gray background
//        super.renderBackground(guiGraphics)
        RenderSystem.enableBlend()
        val resourcelocation =
            if (this.minecraft.level == null) MENU_LIST_BACKGROUND else INWORLD_MENU_LIST_BACKGROUND
        guiGraphics.blit(
            resourcelocation,
            x,  // 控件左上角X
            y,  // 控件左上角Y
            width,   // 控件宽度
            height,  // 控件高度
            0f,      // uOffset从纹理左上角开始
            0f,      // vOffset从纹理左上角开始
            width,   // 使用控件宽度作为纹理绘制宽度
            height,  // 使用控件高度作为纹理绘制高度
            32,   // 纹理总宽度（假设纹理尺寸与控件一致）
            32   // 纹理总高度
        )
        RenderSystem.disableBlend()
    }
    private fun isExpanded(bone: OBone): Boolean = expandedBones.contains(bone.name)

    private fun toggleExpand(bone: OBone) {
        if (isExpanded(bone)) {
            expandedBones.remove(bone.name)
            // 折叠时，需要移除所有子孙节点的展开状态 (可选)
            // removeDescendantExpansion(bone)
        } else {
            expandedBones.add(bone.name)
        }
        refreshVisibleNodes()
    }

    internal fun refreshVisibleNodes() {
        flatVisibleNodes.clear()
        if (model == null) return // 没有模型数据无法构建
        rootBones.forEach { addNodeToList(it, 0) }
        contentHeight = flatVisibleNodes.size * itemHeight
    }

    // 递归添加节点
    private fun addNodeToList(element: Any, level: Int) {
        flatVisibleNodes.add(Pair(element, level))

        if (element is OBone && isExpanded(element)) {
            // 添加子骨骼
            model?.bones?.values?.filter { it.parentName == element.name }?.forEach { childBone ->
                addNodeToList(childBone, level + 1)
            }
            // 添加方块
            element.cubes.forEachIndexed { index, cube ->
                // 将 Cube 和其在父骨骼中的索引包装起来，或者创建一个简单的 CubeWrapper
                 data class CubeInfo(val cube: OCube, val index: Int, val parentBone: OBone)
                 addNodeToList(CubeInfo(cube, index, element), level + 1) // 传递 CubeInfo
            }
        }
    }

    override fun scrollbarVisible(): Boolean {
        return super.scrollbarVisible() && contentHeight > innerHeight // 使用 innerHeight
    }

    override fun getInnerHeight(): Int {
        // AbstractScrollWidget 的 innerHeight 是 height - 4
        return height - 4
    }

     override fun scrollRate(): Double {
         return (font.lineHeight * 1.5)
     }

    override fun getMaxScrollAmount(): Int {
         // 内容总高度 - 可见区域高度
        return max(0, this.contentHeight - this.innerHeight)
    }

    override fun renderContents(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        val minY = this.y + 2
        minY + this.innerHeight // 可见区域底部
        val startY = minY - this.scrollAmount().toInt()
        val startX = this.x + 2

        var currentY = startY
        for ((element, level) in flatVisibleNodes) {
            val elementTop = currentY
            val elementBottom = elementTop + itemHeight

            // 只渲染在可见滚动区域内的条目
            if (elementBottom >= this.y && elementTop <= this.y + this.height) {
                val nodeX = startX + level * indentation
                val nodeWidth = this.width - 4 - level * indentation

                 // --- 绘制背景和选中状态 ---
                 val isSelected = selectedElement === element || (selectedElement is CubeInfo && element is CubeInfo && (selectedElement as CubeInfo).cube === element.cube)
                 val isMouseOverItem = isMouseOver(mouseX.toDouble(), mouseY.toDouble()) && mouseY >= elementTop && mouseY < elementBottom

                 if (isSelected) {
                     guiGraphics.fill(nodeX, elementTop, nodeX + nodeWidth, elementBottom, 0x80FFFFFF.toInt())
                 } else if (isMouseOverItem) {
                     guiGraphics.fill(nodeX, elementTop, nodeX + nodeWidth, elementBottom, 0x40FFFFFF.toInt())
                 }

                // --- 绘制展开/折叠图标 ---
                val iconX = nodeX - 8
                val canExpand = element is OBone && (model?.bones?.values?.any { it.parentName == element.name } == true || element.cubes.isNotEmpty())

                if (canExpand) {
                    val icon = if (isExpanded(element)) "-" else "+"
                    guiGraphics.drawString(font, icon, iconX, elementTop + (itemHeight - font.lineHeight) / 2, 0xFFFFFF.toInt())
                }

                // --- 绘制节点名称 ---
                val displayName = when (element) {
                    is OBone -> Component.literal(element.name)
                    is CubeInfo -> Component.literal("  Cube ${element.index}") // 传递的是 CubeInfo
                    else -> Component.literal("Unknown")
                }
                guiGraphics.drawString(font, displayName, nodeX, elementTop + (itemHeight - font.lineHeight) / 2, 0xFFFFFF.toInt())
            }

            currentY += itemHeight
            // 优化：如果当前 Y 已超过可见区域底部，可以提前退出循环
            // if (currentY > maxY) break // 注意：这可能在 AbstractScrollWidget 中已处理
        }
    }

     override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY)) {
            return false // 不在控件区域内
        }

        val minY = this.y + 2
        val startY = minY - this.scrollAmount().toInt()
        val relativeY = mouseY - startY

        if (relativeY < 0) return false // 点击在可见内容上方

        val clickedIndex = (relativeY / itemHeight).toInt()

        if (clickedIndex >= 0 && clickedIndex < flatVisibleNodes.size) {
            val (clickedElement, level) = flatVisibleNodes[clickedIndex]
            val clickX = mouseX - (this.x + 2) // 相对于内容区的 X
            startY + clickedIndex * itemHeight

            // --- 处理展开/折叠 (仅对 OBone) ---
            if (clickedElement is OBone) {
                val iconMinX = level * indentation - 8
                val iconMaxX = level * indentation
                 val canExpand = model?.bones?.values?.any { it.parentName == clickedElement.name } == true || clickedElement.cubes.isNotEmpty()
                if (canExpand && clickX >= iconMinX && clickX < iconMaxX) {
                     toggleExpand(clickedElement)
                     return true // 事件已处理
                }
            }

            // --- 处理选择 ---
            // 点击节点名称区域
             val textMinX = level * indentation
             if (clickX >= textMinX) {
                 val selectionTarget = if (clickedElement is CubeInfo) clickedElement.cube else clickedElement
                 if (selectedElement !== selectionTarget) {
                     selectedElement = selectionTarget
                     onSelectionChanged(selectedElement)
                 } else {
                     // 再次点击可以取消选择 (可选)
                     // selectedElement = null
                     // onSelectionChanged(null)
                 }
                 return true // 事件已处理
             }
        }
        // 如果点击不在任何节点上，但仍在控件内，则认为是点击了滚动条或空白处
        // 让父类处理滚动条点击
        return super.mouseClicked(mouseX, mouseY, button)
    }


    override fun updateWidgetNarration(output: NarrationElementOutput) {
        // TODO: 添加辅助功能旁白
    }

    /**
     * Sets the selected element in the tree view and expands parent nodes as needed
     * @param element The element to select (OBone or OCube)
     */
    fun setSelectedElement(element: Any?) {
        when (element) {
            is OBone -> {
                // Find and expand all parent bones
                expandParentBones(element)
                selectedElement = element
                onSelectionChanged(element)
            }
            is OCube -> {
                // Find the parent bone of this cube
                val parentBone = model?.bones?.values?.find { it.cubes.contains(element) }
                if (parentBone != null) {
                    // Expand all parent bones
                    expandParentBones(parentBone)
                    // Expand the direct parent to show the cube
                    expandedBones.add(parentBone.name)
                    refreshVisibleNodes()
                }

                // Find the CubeInfo wrapper for this cube
                val cubeInfo = flatVisibleNodes.find {
                    (it.first is CubeInfo) && (it.first as CubeInfo).cube === element
                }?.first

                selectedElement = cubeInfo ?: element
                onSelectionChanged(element)
            }
            else -> {
                selectedElement = null
                onSelectionChanged(null)
            }
        }

        // Ensure the selected element is visible by scrolling to it
        scrollToSelectedElement()
    }

    /**
     * Expands all parent bones of the given bone
     */
    private fun expandParentBones(bone: OBone) {
        var currentBone = bone
        var parentName = currentBone.parentName

        // Expand the bone itself
        expandedBones.add(currentBone.name)

        // Expand all parent bones
        while (parentName != null) {
            val parentBone = model?.bones?.get(parentName)
            if (parentBone != null) {
                expandedBones.add(parentBone.name)
                parentName = parentBone.parentName
            } else {
                break
            }
        }

        refreshVisibleNodes()
    }

    /**
     * Scrolls the view to make the selected element visible
     */
    private fun scrollToSelectedElement() {
        val selectedIndex = flatVisibleNodes.indexOfFirst {
            it.first === selectedElement ||
            (selectedElement is OCube && it.first is CubeInfo && (it.first as CubeInfo).cube === selectedElement)
        }

        if (selectedIndex >= 0) {
            val elementY = selectedIndex * itemHeight
            val visibleStart = scrollAmount().toInt()
            val visibleEnd = visibleStart + innerHeight

            if (elementY < visibleStart) {
                // Element is above visible area, scroll up
                setScrollAmount(elementY.toDouble())
            } else if (elementY + itemHeight > visibleEnd) {
                // Element is below visible area, scroll down
                setScrollAmount((elementY + itemHeight - innerHeight).toDouble())
            }
        }
    }

    // 临时的 CubeInfo 包装类，用于在列表中区分 Cube
    private data class CubeInfo(val cube: OCube, val index: Int, val parentBone: OBone)
}
