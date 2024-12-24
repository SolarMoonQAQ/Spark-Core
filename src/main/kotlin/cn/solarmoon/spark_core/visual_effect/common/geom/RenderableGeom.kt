package cn.solarmoon.spark_core.visual_effect.common.geom

import cn.solarmoon.spark_core.phys.baseCopy
import cn.solarmoon.spark_core.phys.toDQuaternion
import cn.solarmoon.spark_core.phys.toDVector3
import cn.solarmoon.spark_core.phys.toQuaterniond
import cn.solarmoon.spark_core.phys.toVector3d
import org.ode4j.ode.DBox
import org.ode4j.ode.DGeom
import org.ode4j.ode.OdeHelper
import java.awt.Color

/**
 * 在客户端侧对可渲染箱进行操作，如果在服务端无需进行操作，将操作仍在客户端进行，然后通过服务端同步刷新颜色或当前box即可
 */
class RenderableGeom {

    var maxTime: Int = 10
    var defaultColor: Color = Color.WHITE
    var tick = 0
    var colorTick = 0
    var maxColorTick = 5
    var color: Color = defaultColor
        private set
    var box: DGeom? = null
    var lastBox: DGeom? = null
    var isRemoved: Boolean = false
    var currentBox: DGeom? = null

    fun setColor(color: Color) {
        this.color = color
        colorTick = 0
    }

    fun tick() {
        if (tick >= maxTime) {
            remove()
        } else {
            tick++
        }

        if (color != defaultColor) {
            if (colorTick < maxColorTick) colorTick++
            else color = defaultColor
        }
    }

    fun refresh(box: DGeom, straight: Boolean = false) {//将缓存数据正式存入OBB对象
        currentBox = box
        tick = 0
        if (straight) lastBox = box
        if (this.box != null) this.lastBox = (this.box as DBox).baseCopy()
        this.box = (box as DBox).baseCopy()
    }

    fun getBox(partialTicks: Float): DGeom? {
        if (box == null) return null
        if (lastBox == null) return box
        if (lastBox is DBox && box is DBox) {
            return lerp(lastBox as DBox, box as DBox, partialTicks.toDouble())
        }
        return box
    }

    private fun lerp(b1: DBox, b2: DBox, partialTicks: Double): DBox {
        val p1 = b1.position.toVector3d()
        val s1 = b1.lengths.toVector3d()
        val q1 = b1.quaternion.toQuaterniond()

        val p2 = b2.position.toVector3d()
        val s2 = b2.lengths.toVector3d()
        val q2 = b2.quaternion.toQuaterniond()

        val rp = p1.lerp(p2, partialTicks).toDVector3()//混合后的位置
        val rs = s1.lerp(s2, partialTicks).toDVector3()//混合后的尺寸
        val rq = q1.slerp(q2, partialTicks).toDQuaternion()//混合后的旋转
        return OdeHelper.createBox(rs).apply { position = rp; quaternion = rq }
    }

    fun remove() {
        isRemoved = true
    }

}