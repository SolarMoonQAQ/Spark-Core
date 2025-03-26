package cn.solarmoon.spark_core.physics.pool

import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject

object TransformPool: KPool<Transform>(
    object : BasePooledObjectFactory<Transform>() {
        override fun create(): Transform {
            return Transform()
        }

        override fun wrap(obj: Transform): PooledObject<Transform> {
            return DefaultPooledObject(obj)
        }

        override fun passivateObject(p: PooledObject<Transform>) {
            p.`object`.apply {
                translation = Vector3f()
                rotation = Quaternion()
                scale = Vector3f(1f, 1f, 1f)
            }
        }
    }
) {

}