package cn.solarmoon.spark_core.entity.pathfinding

import net.minecraft.util.Mth
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

/**
 * Credit: [Mowzie的生物](https://github.com/BobMowzie/MowziesMobs/blob/master/src/main/java/com/bobmowzie/mowziesmobs/server/ai/MMPathNavigateGround.java)
 * 解决了大碰撞箱情况下寻路在正轴来回碰撞的问题
 */
class NaturalNavigateGround(mob: Mob, level: Level): GroundPathNavigation(mob, level) {

    override fun followThePath() {
        val path = this.path ?: return
        val entityPos = this.tempMobPos
        var pathLength = path.nodeCount;
        for (i in path.nextNodeIndex until path.nodeCount) {
            if (path.getNode(i).y.toDouble() != floor(entityPos.y)) {
                pathLength = i
                break
            }
        }
        val base = entityPos.add(-this.mob.bbWidth * 0.5, 0.0, -this.mob.bbWidth * 0.5)
        val max = base.add(this.mob.bbWidth.toDouble(), this.mob.bbHeight.toDouble(), this.mob.bbWidth.toDouble())
        if (this.tryShortcut(path, Vec3(this.mob.x, this.mob.y, this.mob.z), pathLength, base, max)) {
            if (this.isAt(path, 0.5F) || this.atElevationChange(path) && this.isAt(path, this.mob.bbWidth * 0.5F)) {
                path.nextNodeIndex = path.nextNodeIndex + 1
            }
        }
        this.doStuckDetection(entityPos)
    }

    fun isAt(path: Path, threshold: Float): Boolean {
        val pathPos = path.getNextEntityPos(this.mob);
        return Mth.abs((this.mob.x - pathPos.x).toFloat()) < threshold &&
                Mth.abs((this.mob.z - pathPos.z).toFloat()) < threshold &&
                abs(this.mob.y - pathPos.y) < 1.0
    }

    fun atElevationChange(path: Path): Boolean {
        val curr = path.nextNodeIndex;
        val end = path.nodeCount.coerceAtMost(curr + Mth.ceil(this.mob.bbWidth * 0.5F) + 1)
        val currY = path.getNode(curr).y
        for (i in curr + 1 until end) {
            if (path.getNode(i).y != currY) {
                return true
            }
        }
        return false
    }

    fun tryShortcut(path: Path, entityPos: Vec3, pathLength: Int, base: Vec3, max: Vec3): Boolean {
        for (i in pathLength - 1 downTo path.nextNodeIndex + 1) {
            val vec = path.getEntityPosAtNode(this.mob, i).subtract(entityPos)
            if (this.sweep(vec, base, max)) {
                path.nextNodeIndex = i
                return false
            }
        }
        return true
    }

    val EPSILON = 1.0E-8F

    // Based off of https://github.com/andyhall/voxel-aabb-sweep/blob/d3ef85b19c10e4c9d2395c186f9661b052c50dc7/index.js
    private fun sweep(vec: Vec3, base: Vec3, max: Vec3): Boolean {
        val maxT = vec.length().toFloat()
        if (maxT >= EPSILON) {
            val tr = FloatArray(3)
            val ldi = IntArray(3)
            val tri = IntArray(3)
            val step = IntArray(3)
            val tDelta = FloatArray(3)
            val tNext = FloatArray(3)
            val normed = FloatArray(3)

            for (i in 0..2) {
                val value = element(vec, i)
                val dir = value >= 0.0f
                step[i] = if (dir) 1 else -1
                val lead = element(if (dir) max else base, i)
                tr[i] = element(if (dir) base else max, i)
                ldi[i] = leadEdgeToInt(lead, step[i])
                tri[i] = trailEdgeToInt(tr[i], step[i])
                normed[i] = value / maxT
                tDelta[i] = Mth.abs(maxT / value)
                val dist = if (dir) (ldi[i] + 1).toFloat() - lead else lead - ldi[i].toFloat()
                tNext[i] = if (tDelta[i] < Float.POSITIVE_INFINITY) tDelta[i] * dist else Float.POSITIVE_INFINITY
            }
        }
        return true
    }

    fun leadEdgeToInt(coord: Float, step: Int): Int {
        return Mth.floor(coord - step * EPSILON);
    }

    fun trailEdgeToInt(coord: Float, step: Int): Int {
        return Mth.floor(coord + step * EPSILON);
    }

    fun element(v: Vec3, i: Int): Float {
        return when (i) {
            0 -> v.x.toFloat()
            1 -> v.y.toFloat()
            2 -> v.z.toFloat()
            else -> 0.0f
        }
    }

}