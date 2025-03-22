package cn.solarmoon.spark_core.physics.collision

import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * VoxelShape转换工具类，用于将Minecraft的VoxelShape转换为JME3 Bullet物理引擎的碰撞体。
 * 可以处理不规则形状，将它们转换为复合碰撞体。
 */
object VoxelShapeConverter {

    // AABB合并优化的阈值：合并后允许的最大额外体积比例(0.25表示最多允许25%的额外体积)
    private const val MERGE_VOLUME_THRESHOLD = 0.01
    
    // AABB相邻判定的容差值
    private const val ADJACENCY_EPSILON = 1.0E-6

    /**
     * 将VoxelShape转换为PhysicsRigidBody
     * 
     * @param voxelShape 要转换的VoxelShape
     * @param owner 物理主机
     * @param position 方块位置（可选）
     * @param mass 质量，0表示静态物体（默认为0）
     * @param name 碰撞体名称（默认为"voxel_body"）
     * @return 对应的PhysicsRigidBody实例
     */
    fun toPhysicsRigidBody(
        voxelShape: VoxelShape,
        owner: PhysicsHost,
        position: BlockPos? = null,
        mass: Float = 0f,
        name: String = "voxel_body"
    ): PhysicsRigidBody {
        val collisionShape = toCollisionShape(voxelShape)
        
        return if (position != null) {
            PhysicsRigidBody(owner, collisionShape, position)
        } else {
            PhysicsRigidBody(name, owner, collisionShape, mass)
        }
    }

    /**
     * 将VoxelShape转换为复合碰撞形状
     * 
     * @param voxelShape 要转换的VoxelShape
     * @return 对应的CollisionShape实例
     */
    fun toCollisionShape(voxelShape: VoxelShape): CollisionShape {
        // 如果是空的VoxelShape，返回一个很小的BoxCollisionShape
        if (voxelShape.isEmpty()) {
            return BoxCollisionShape(0.01f)
        }

        // 获取优化后的AABB列表
//        val optimizedAabbs = getOptimizedAABBs(voxelShape)
        val optimizedAabbs = voxelShape.toAabbs()
        // 如果只有一个AABB，直接使用简单的BoxCollisionShape
        if (optimizedAabbs.size == 1) {
            return createBoxFromAABB(optimizedAabbs[0])
        }

        // 对于多个AABB的情况，创建复合形状
        val compoundShape = CompoundCollisionShape()
        
        for (aabb in optimizedAabbs) {
            val boxShape = createBoxFromAABB(aabb)
            val transform = createTransformFromAABB(aabb)
            compoundShape.addChildShape(boxShape, transform)
        }
        
        return compoundShape
    }

    /**
     * 从AABB创建BoxCollisionShape
     */
    private fun createBoxFromAABB(aabb: AABB): BoxCollisionShape {
        val halfExtents = Vector3f(
            (aabb.xsize / 2).toFloat(),
            (aabb.ysize / 2).toFloat(),
            (aabb.zsize / 2).toFloat()
        )
        return BoxCollisionShape(halfExtents)
    }

    /**
     * 从AABB创建Transform
     */
    private fun createTransformFromAABB(aabb: AABB): Transform {
        // 计算中心点相对于方块中心(0.5, 0.5, 0.5)的偏移
        val offsetX = aabb.center.x.toFloat() - 0.5f
        val offsetY = aabb.center.y.toFloat() - 0.5f
        val offsetZ = aabb.center.z.toFloat() - 0.5f
        
        return Transform(
            Vector3f(offsetX, offsetY, offsetZ),
            Quaternion.IDENTITY
        )
    }

    /**
     * 将VoxelShape中所有的AABB提取出来并优化
     * 
     * @param voxelShape 要处理的VoxelShape
     * @return 优化后的AABB列表
     */
    fun getOptimizedAABBs(voxelShape: VoxelShape): List<AABB> {
        // 首先获取所有的AABB
        val aabbs = voxelShape.toAabbs()
        
        // 如果只有一个AABB或者是空的，直接返回
        if (aabbs.size <= 1) {
            return aabbs
        }
        
        // 将AABB列表传入合并算法
        return mergeAABBs(aabbs)
    }
    
    /**
     * AABB合并算法，合并相邻的AABB以减少碰撞形状的数量
     * 
     * @param aabbs 原始AABB列表
     * @return 合并后的AABB列表
     */
    private fun mergeAABBs(aabbs: List<AABB>): List<AABB> {
        // 创建可变列表，用于存储合并过程中的AABB
        val mergeableAabbs = aabbs.toMutableList()
        
        // 如果AABB数量太少，不进行合并
        if (mergeableAabbs.size <= 2) {
            return mergeableAabbs
        }
        
        // 持续进行合并，直到无法进一步合并
        var merged: Boolean
        do {
            merged = false
            
            // 寻找最佳合并对
            var bestMergeI = -1
            var bestMergeJ = -1
            var bestMergeVolumeRatio = Double.MAX_VALUE
            
            // 遍历所有可能的AABB对
            for (i in 0 until mergeableAabbs.size - 1) {
                for (j in i + 1 until mergeableAabbs.size) {
                    val a = mergeableAabbs[i]
                    val b = mergeableAabbs[j]
                    
                    // 检查是否相邻或重叠
                    if (!areAdjacent(a, b) && !doOverlap(a, b)) {
                        continue
                    }
                    
                    // 计算合并后的体积比例
                    val volumeRatio = calculateVolumeRatio(a, b)
                    
                    // 如果体积比例小于阈值且比之前找到的更好，更新最佳合并对
                    if (volumeRatio < MERGE_VOLUME_THRESHOLD && volumeRatio < bestMergeVolumeRatio) {
                        bestMergeI = i
                        bestMergeJ = j
                        bestMergeVolumeRatio = volumeRatio
                    }
                }
            }
            
            // 如果找到可合并的对，进行合并
            if (bestMergeI >= 0 && bestMergeJ >= 0) {
                val a = mergeableAabbs[bestMergeI]
                val b = mergeableAabbs[bestMergeJ]
                
                // 合并这两个AABB
                val mergedAABB = merge(a, b)
                
                // 从列表中移除原始的两个AABB
                // 注意：先移除索引较大的，以免影响索引
                mergeableAabbs.removeAt(bestMergeJ)
                mergeableAabbs.removeAt(bestMergeI)
                
                // 将合并后的AABB添加回列表
                mergeableAabbs.add(mergedAABB)
                
                merged = true
            }
            
        } while (merged && mergeableAabbs.size > 1)
        
        return mergeableAabbs
    }
    
    /**
     * 计算合并两个AABB后的体积比例
     * 
     * @param a 第一个AABB
     * @param b 第二个AABB
     * @return 合并后的额外体积比例 (合并后体积 - 原始体积和) / 原始体积和
     */
    private fun calculateVolumeRatio(a: AABB, b: AABB): Double {
        // 计算原始体积
        val volumeA = a.xsize * a.ysize * a.zsize
        val volumeB = b.xsize * b.ysize * b.zsize
        val originalVolume = volumeA + volumeB
        
        // 计算合并后的体积
        val mergedAABB = merge(a, b)
        val mergedVolume = mergedAABB.xsize * mergedAABB.ysize * mergedAABB.zsize
        
        // 计算额外体积比例
        return (mergedVolume - originalVolume) / originalVolume
    }
    
    /**
     * 检查两个AABB是否相邻（共享面或边）
     * 
     * @param a 第一个AABB
     * @param b 第二个AABB
     * @return 是否相邻
     */
    private fun areAdjacent(a: AABB, b: AABB): Boolean {
        // 检查X轴方向是否相邻
        val touchX = abs(a.maxX - b.minX) < ADJACENCY_EPSILON || abs(a.minX - b.maxX) < ADJACENCY_EPSILON
        val overlapY = a.minY <= b.maxY + ADJACENCY_EPSILON && a.maxY >= b.minY - ADJACENCY_EPSILON
        val overlapZ = a.minZ <= b.maxZ + ADJACENCY_EPSILON && a.maxZ >= b.minZ - ADJACENCY_EPSILON
        
        // 检查Y轴方向是否相邻
        val touchY = abs(a.maxY - b.minY) < ADJACENCY_EPSILON || abs(a.minY - b.maxY) < ADJACENCY_EPSILON
        val overlapX = a.minX <= b.maxX + ADJACENCY_EPSILON && a.maxX >= b.minX - ADJACENCY_EPSILON
        val overlapZ2 = a.minZ <= b.maxZ + ADJACENCY_EPSILON && a.maxZ >= b.minZ - ADJACENCY_EPSILON
        
        // 检查Z轴方向是否相邻
        val touchZ = abs(a.maxZ - b.minZ) < ADJACENCY_EPSILON || abs(a.minZ - b.maxZ) < ADJACENCY_EPSILON
        val overlapX2 = a.minX <= b.maxX + ADJACENCY_EPSILON && a.maxX >= b.minX - ADJACENCY_EPSILON
        val overlapY2 = a.minY <= b.maxY + ADJACENCY_EPSILON && a.maxY >= b.minY - ADJACENCY_EPSILON
        
        return (touchX && overlapY && overlapZ) || 
               (touchY && overlapX && overlapZ2) || 
               (touchZ && overlapX2 && overlapY2)
    }
    
    /**
     * 检查两个AABB是否重叠
     * 
     * @param a 第一个AABB
     * @param b 第二个AABB
     * @return 是否重叠
     */
    private fun doOverlap(a: AABB, b: AABB): Boolean {
        // 两个AABB在三个轴方向上都有重叠，则它们重叠
        return a.minX <= b.maxX + ADJACENCY_EPSILON && a.maxX >= b.minX - ADJACENCY_EPSILON &&
               a.minY <= b.maxY + ADJACENCY_EPSILON && a.maxY >= b.minY - ADJACENCY_EPSILON &&
               a.minZ <= b.maxZ + ADJACENCY_EPSILON && a.maxZ >= b.minZ - ADJACENCY_EPSILON
    }
    
    /**
     * 合并两个AABB
     * 
     * @param a 第一个AABB
     * @param b 第二个AABB
     * @return 合并后的AABB
     */
    private fun merge(a: AABB, b: AABB): AABB {
        // 计算包含两个AABB的最小AABB
        val minX = min(a.minX, b.minX)
        val minY = min(a.minY, b.minY)
        val minZ = min(a.minZ, b.minZ)
        val maxX = max(a.maxX, b.maxX)
        val maxY = max(a.maxY, b.maxY)
        val maxZ = max(a.maxZ, b.maxZ)
        
        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }
    
    /**
     * 从BlockState创建PhysicsRigidBody的示例方法
     * 
     * @param blockState 要转换的方块状态
     * @param owner 物理主机
     * @param position 方块位置
     * @return 对应的PhysicsRigidBody实例
     */
    fun createRigidBodyFromBlockState(
        blockState: BlockState,
        owner: PhysicsHost,
        position: BlockPos
    ): PhysicsRigidBody {
        // 获取方块状态对应的VoxelShape
        val voxelShape = blockState.getCollisionShape(
            EmptyBlockGetter.INSTANCE, 
            BlockPos.ZERO, 
            CollisionContext.empty()
        )
        
        // 转换为PhysicsRigidBody
        return toPhysicsRigidBody(
            voxelShape = voxelShape,
            owner = owner,
            position = position,
            name = "${blockState.block.descriptionId}_${position.x}_${position.y}_${position.z}"
        )
    }
    
    /**
     * 测试AABB合并算法的效果
     * 创建一些典型的AABB排列，并应用合并算法
     * 
     * @return 测试结果报告
     */
    fun testAABBMerging(): String {
        // 创建测试用的AABB列表
        val testCases = listOf(
            // 测试用例1: 两个相邻的方块 (应该被合并)
            listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                AABB(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)
            ),
            
            // 测试用例2: 四个形成正方形的方块 (应该被合并为一个大正方形)
            listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                AABB(1.0, 0.0, 0.0, 2.0, 1.0, 1.0),
                AABB(0.0, 0.0, 1.0, 1.0, 1.0, 2.0),
                AABB(1.0, 0.0, 1.0, 2.0, 1.0, 2.0)
            ),
            
            // 测试用例3: L形结构的三个方块 (应该被合并为一个L形)
            listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                AABB(1.0, 0.0, 0.0, 2.0, 1.0, 1.0),
                AABB(0.0, 0.0, 1.0, 1.0, 1.0, 2.0)
            ),
            
            // 测试用例4: 不相邻的方块 (不应该被合并)
            listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                AABB(2.0, 0.0, 0.0, 3.0, 1.0, 1.0),
                AABB(0.0, 0.0, 2.0, 1.0, 1.0, 3.0)
            ),
            
            // 测试用例5: 复杂的楼梯形状 (部分应该被合并)
            listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
                AABB(0.0, 0.0, 0.0, 0.5, 1.0, 1.0),
                AABB(0.5, 0.5, 0.0, 1.0, 1.0, 1.0)
            )
        )
        
        // 用于存储测试结果的字符串
        val resultBuilder = StringBuilder("AABB合并算法测试结果:\n\n")
        
        // 计算体积的辅助函数
        fun calculateVolume(aabb: AABB): Double {
            return (aabb.maxX - aabb.minX) * (aabb.maxY - aabb.minY) * (aabb.maxZ - aabb.minZ)
        }
        
        fun calculateTotalVolume(aabbs: List<AABB>): Double {
            return aabbs.sumOf { calculateVolume(it) }
        }
        
        // 对每个测试用例应用合并算法
        testCases.forEachIndexed { index, aabbs ->
            val mergedAABBs = mergeAABBs(aabbs)
            val originalVolume = calculateTotalVolume(aabbs)
            val mergedVolume = calculateTotalVolume(mergedAABBs)
            
            resultBuilder.appendLine("测试用例 ${index + 1}:")
            resultBuilder.appendLine("  原始AABB数量: ${aabbs.size}")
            resultBuilder.appendLine("  合并后AABB数量: ${mergedAABBs.size}")
            resultBuilder.appendLine("  原始总体积: $originalVolume")
            resultBuilder.appendLine("  合并后总体积: $mergedVolume")
            resultBuilder.appendLine("  体积比率: ${mergedVolume / originalVolume}")
            
            resultBuilder.appendLine("  合并后的AABB列表:")
            mergedAABBs.forEachIndexed { aabbIndex, aabb ->
                resultBuilder.appendLine("    AABB $aabbIndex: 最小(${aabb.minX}, ${aabb.minY}, ${aabb.minZ}), 最大(${aabb.maxX}, ${aabb.maxY}, ${aabb.maxZ})")
            }
            resultBuilder.appendLine()
        }
        
        return resultBuilder.toString()
    }
}

/**
 * VoxelShape的扩展函数，直接将VoxelShape转换为PhysicsRigidBody
 */
fun VoxelShape.toPhysicsRigidBody(
    owner: PhysicsHost,
    position: BlockPos? = null,
    mass: Float = 0f,
    name: String = "voxel_body"
): PhysicsRigidBody {
    return VoxelShapeConverter.toPhysicsRigidBody(this, owner, position, mass, name)
}

/**
 * VoxelShape的扩展函数，直接将VoxelShape转换为CollisionShape
 */
fun VoxelShape.toCollisionShape(): CollisionShape {
    return VoxelShapeConverter.toCollisionShape(this)
}

/**
 * BlockState的扩展函数，从方块状态直接创建PhysicsRigidBody
 */
fun BlockState.toPhysicsRigidBody(
    owner: PhysicsHost,
    position: BlockPos
): PhysicsRigidBody {
    return VoxelShapeConverter.createRigidBodyFromBlockState(this, owner, position)
} 