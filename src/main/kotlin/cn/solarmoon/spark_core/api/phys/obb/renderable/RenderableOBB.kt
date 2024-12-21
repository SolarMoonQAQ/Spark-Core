package cn.solarmoon.spark_core.api.phys.obb.renderable

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.api.phys.lerp
import cn.solarmoon.spark_core.api.phys.toDQuaternion
import cn.solarmoon.spark_core.api.phys.toDVector3
import cn.solarmoon.spark_core.api.phys.toQuaterniond
import cn.solarmoon.spark_core.api.phys.toVector3d
import org.ode4j.ode.DBox
import org.ode4j.ode.DGeom
import org.ode4j.ode.OdeHelper
import java.awt.Color

/**
 * 在客户端侧对可渲染箱进行操作，如果在服务端无需进行操作，将操作仍在客户端进行，然后通过服务端同步刷新颜色或当前box即可
 */
class RenderableOBB {

    var maxTime: Int = 10
    var defaultColor: Color = Color.WHITE
    var tick = 0
    var colorTick = 0
    var maxColorTick = 5
    var color: Color = defaultColor
        private set
    var box: DGeom? = null
    var boxCache: DGeom? = null
    var refreshCache: Boolean = false
    var lastBox: DGeom? = null
    var isRemoved: Boolean = false

    fun setColor(color: Color) {
        this.color = color
        colorTick = 0
    }

    fun tick() {
        if (tick >= maxTime) {
            remove()
        } else {
            tick++
            this.boxCache?.let { refresh(it, this.refreshCache) }//每tick将缓存正式存入对象一次
        }

        if (color != defaultColor) {
            if (colorTick < maxColorTick) colorTick++
            else color = defaultColor
        }
    }

    fun update(box: DGeom, straight: Boolean = false) {//收到新数据时暂存起来
        this.boxCache = box
        this.refreshCache = straight
    }

    fun refresh(box: DGeom, straight: Boolean = false) {//将缓存数据正式存入OBB对象
        tick = 0
        if (straight) lastBox = box
        else if (this.box != null) this.lastBox = lerp(this.box as DBox,this.box as DBox,0.0)
        this.box = box
//        this.box = box
//        if (straight) lastBox = box
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