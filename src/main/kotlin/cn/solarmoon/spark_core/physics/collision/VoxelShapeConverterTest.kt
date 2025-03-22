package cn.solarmoon.spark_core.physics.collision

import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * VoxelShapeConverter测试类
 * 用于测试AABB合并算法和VoxelShape转换功能
 */
object VoxelShapeConverterTest {

    /**
     * 测试入口方法
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("开始测试VoxelShapeConverter...")
        
        // 测试AABB合并算法
        testAABBMerging()
        
        // 测试VoxelShape转换
        testVoxelShapeConversion()
        
        println("所有测试完成!")
    }
    
    /**
     * 测试AABB合并算法
     */
    private fun testAABBMerging() {
        println("========== 测试AABB合并算法 ==========")
        println(VoxelShapeConverter.testAABBMerging())
    }
    
    /**
     * 测试VoxelShape转换到CollisionShape
     * 注意：由于PhysicsRigidBody需要一个有效的PhysicsHost，我们只测试转换到CollisionShape
     */
    private fun testVoxelShapeConversion() {
        println("========== 测试VoxelShape转换 ==========")
        
        // 创建几个测试用的模拟VoxelShape
        val testShapes = listOf(
            // 单个方块形状
            createMockVoxelShape(listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
            )),
            
            // 楼梯形状
            createMockVoxelShape(listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),  // 下半部分
                AABB(0.0, 0.5, 0.0, 0.5, 1.0, 1.0)   // 上半部分
            )),
            
            // 篱笆形状
            createMockVoxelShape(listOf(
                AABB(0.375, 0.0, 0.375, 0.625, 1.0, 0.625),  // 中间立柱
                AABB(0.0, 0.75, 0.375, 1.0, 0.875, 0.625)    // 横板
            )),
            
            // 复杂形状
            createMockVoxelShape(listOf(
                AABB(0.0, 0.0, 0.0, 1.0, 0.25, 1.0),     // 底座
                AABB(0.25, 0.25, 0.25, 0.75, 1.0, 0.75), // 中间立柱
                AABB(0.125, 0.75, 0.125, 0.875, 1.0, 0.875) // 顶部
            ))
        )
        
        // 测试每个形状的转换
        testShapes.forEachIndexed { index, shape ->
            println("测试形状 ${index + 1}:")
            println("  原始AABB数量: ${shape.toAabbs().count()}")
            
            // 转换为CollisionShape
            val collisionShape = VoxelShapeConverter.toCollisionShape(shape)
            println("  - 转换为CollisionShape成功")
            println("  - 碰撞形状类型: ${collisionShape.javaClass.simpleName}")
            
            // 如果是复合形状，打印子形状数量
            if (collisionShape is CompoundCollisionShape) {
                println("  - 子形状数量: ${collisionShape.countChildren()}")
                println("  - 优化率: ${(shape.toAabbs().count() - collisionShape.countChildren()) / shape.toAabbs().count().toFloat() * 100}%")
            }
            
            println()
        }
        
        // 测试扩展函数
        println("测试VoxelShape扩展函数:")
        val testShape = testShapes[0]
        val collisionShape = testShape.toCollisionShape()
        println("  - 通过扩展函数转换成功: ${collisionShape.javaClass.simpleName}")
        println()
    }
    
    /**
     * 创建模拟的VoxelShape用于测试
     * 
     * 注意：这是一个Mock实现，仅用于测试，不能在实际游戏中使用
     * 
     * @param aabbs 构成VoxelShape的AABB列表
     * @return 模拟的VoxelShape
     */
    private fun createMockVoxelShape(aabbs: List<AABB>): VoxelShape {
        if (aabbs.isEmpty()) {
            return Shapes.empty()
        }
        
        // 从第一个AABB开始
        var result = Shapes.create(
            aabbs[0].minX, aabbs[0].minY, aabbs[0].minZ,
            aabbs[0].maxX, aabbs[0].maxY, aabbs[0].maxZ
        )
        
        // 添加剩余的AABB
        for (i in 1 until aabbs.size) {
            val aabb = aabbs[i]
            val shape = Shapes.create(
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ
            )
            result = Shapes.or(result, shape)
        }
        
        return result
    }
} 