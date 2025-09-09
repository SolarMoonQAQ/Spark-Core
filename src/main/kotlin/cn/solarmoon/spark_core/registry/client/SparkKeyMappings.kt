package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier
import org.lwjgl.glfw.GLFW

object SparkKeyMappings {

    private const val CATEGORY = "key.categories.spark_core.model_editor" // Define category constant

    @JvmStatic
    val OPEN_ANIMATION_DEBUG = SparkCore.REGISTER.keyMapping()
        .id("open_animation_debug") // Keep id simple, prefix added by builder
        .bound(GLFW.GLFW_KEY_F8)
        // .category("key.categories.spark_core.debug") // Optional: separate category
        .build()

    // Key to open the Model Editor (Actual opening logic needs context)
    @JvmStatic
    val OPEN_MODEL_EDITOR = SparkCore.REGISTER.keyMapping()
        .id("open_model_editor")
        .bound(GLFW.GLFW_KEY_K)
        .modifier(KeyModifier.CONTROL) // Ctrl + K
        .category(CATEGORY)
        .conflictContext(KeyConflictContext.IN_GAME) // Only active in game
        .build()

    // Key for Undo in Model Editor
    @JvmStatic
    val MODEL_EDITOR_UNDO = SparkCore.REGISTER.keyMapping()
        .id("model_editor_undo")
        .bound(GLFW.GLFW_KEY_Z)
        .modifier(KeyModifier.CONTROL) // Ctrl + Z
        .category(CATEGORY)
        .conflictContext(KeyConflictContext.GUI) // Only active in GUI
        .build()

    // Key for Redo in Model Editor
    @JvmStatic
    val MODEL_EDITOR_REDO = SparkCore.REGISTER.keyMapping()
        .id("model_editor_redo")
        .bound(GLFW.GLFW_KEY_Y)
        .modifier(KeyModifier.CONTROL) // Ctrl + Y
        .category(CATEGORY)
        .conflictContext(KeyConflictContext.GUI) // Only active in GUI
        .build()
        
    // Key to open JS Script Browser
    @JvmStatic
    val OPEN_JS_SCRIPT_BROWSER = SparkCore.REGISTER.keyMapping()
        .id("open_js_script_browser")
        .bound(GLFW.GLFW_KEY_J)
        .modifier(KeyModifier.CONTROL) // Ctrl + J
        .category("key.categories.spark_core.script_browser")
        .conflictContext(KeyConflictContext.IN_GAME) // Only active in game
        .build()
        
    @JvmStatic
    fun register() {}

}