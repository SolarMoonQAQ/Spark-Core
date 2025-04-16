package cn.solarmoon.spark_core.client.gui.widget

import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OModel // 需要 OModel 来查找子骨骼
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractScrollWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import kotlin.math.max

class ModelTreeViewWidget(
    x: Int, y: Int, width: Int, height: Int,
    private val model: OModel?, // 需要持有 OModel 的引用来查找子骨骼
    private val itemHeight: Int = 12,
    private val indentation: Int = 10,
    private val onSelectionChanged: (Any?) -> Unit // 回调传递 OBone 或 OCube
) : AbstractScrollWidget(x, y, width, height, Component.empty()) {

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

    fun setNodes(rootNodes: List<OBone>) {
        this.rootBones = rootNodes
        this.expandedBones.clear()
        // 默认展开根骨骼
        rootNodes.forEach { expandedBones.add(it.name) }
        refreshVisibleNodes()
        setScrollAmount(0.0)
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
        val minY = this.getY() + 2
        val maxY = minY + this.innerHeight // 可见区域底部
        val startY = minY - this.scrollAmount().toInt()
        val startX = this.getX() + 2

        var currentY = startY
        for ((element, level) in flatVisibleNodes) {
            val elementTop = currentY
            val elementBottom = elementTop + itemHeight

            // 只渲染在可见滚动区域内的条目
            if (elementBottom >= this.getY() && elementTop <= this.getY() + this.height) {
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
                    val icon = if (isExpanded(element as OBone)) "-" else "+"
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

        val minY = this.getY() + 2
        val startY = minY - this.scrollAmount().toInt()
        val relativeY = mouseY - startY

        if (relativeY < 0) return false // 点击在可见内容上方

        val clickedIndex = (relativeY / itemHeight).toInt()

        if (clickedIndex >= 0 && clickedIndex < flatVisibleNodes.size) {
            val (clickedElement, level) = flatVisibleNodes[clickedIndex]
            val clickX = mouseX - (this.getX() + 2) // 相对于内容区的 X
            val elementTop = startY + clickedIndex * itemHeight

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

    // 临时的 CubeInfo 包装类，用于在列表中区分 Cube
    private data class CubeInfo(val cube: OCube, val index: Int, val parentBone: OBone)
}
