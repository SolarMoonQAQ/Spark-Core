package cn.solarmoon.spark_core.physics.pool

import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.checkerframework.checker.units.qual.t

/**
 * 对Common Pool库的具有kotlin风格的包装池接口
 */
abstract class KPool<T>(
    factory: BasePooledObjectFactory<T>,
    config: GenericObjectPoolConfig<T> = GenericObjectPoolConfig<T>().apply {
        maxTotal = 100
        maxIdle = 50
        minIdle = 10
        testOnBorrow = true
    }
): GenericObjectPool<T>(
    factory, config
) {

    inline fun <R> use(block: (T) -> R): R {
        val obj = borrowObject()
        return try {
            block(obj)
        } finally {
           returnObject(obj)
        }
    }

    /**
     * 获取当前池状态
     */
    val stats: String get() = "Active: $numActive, Idle: $numIdle"

}