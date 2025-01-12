package cn.solarmoon.spark_core.animation.sync

import java.util.concurrent.atomic.AtomicInteger

class SyncedAnimation(

) {

    val id = ID_COUNTER.incrementAndGet()

    companion object {
        @JvmStatic
        val ID_COUNTER = AtomicInteger()
    }

}