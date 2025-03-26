package cn.solarmoon.spark_core.physics.pool

import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject

object QuaternionPool: KPool<Quaternion>(
    object : BasePooledObjectFactory<Quaternion>() {
        override fun create(): Quaternion {
            return Quaternion()
        }

        override fun wrap(obj: Quaternion): PooledObject<Quaternion> {
            return DefaultPooledObject(obj)
        }

        override fun passivateObject(p: PooledObject<Quaternion>) {
            p.`object`.set(0f, 0f, 0f, 0f)
        }
    }
) {

}