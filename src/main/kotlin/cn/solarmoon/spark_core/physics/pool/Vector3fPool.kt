package cn.solarmoon.spark_core.physics.pool

import com.jme3.math.Vector3f
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig

object Vector3fPool: KPool<Vector3f>(
    object : BasePooledObjectFactory<Vector3f>() {
        override fun create(): Vector3f {
            return Vector3f()
        }

        override fun wrap(obj: Vector3f): PooledObject<Vector3f> {
            return DefaultPooledObject(obj)
        }

        override fun passivateObject(p: PooledObject<Vector3f>) {
            p.`object`.set(0f, 0f, 0f)
        }
    }
) {

}