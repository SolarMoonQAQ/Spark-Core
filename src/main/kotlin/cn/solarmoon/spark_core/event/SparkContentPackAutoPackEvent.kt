package cn.solarmoon.spark_core.event

import com.google.gson.JsonObject
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import java.nio.file.Path

abstract class SparkContentPackAutoPackEvent : Event() {

    enum class Reason {
        STARTUP,
        RELOAD
    }

    class Pre(
        val modId: String,
        val packName: String,
        val packDirPath: Path,
        val targetZipPath: Path,
        val reason: Reason,
        val metaJson: JsonObject?,
        var shouldPack: Boolean
    ) : SparkContentPackAutoPackEvent(), ICancellableEvent

}
