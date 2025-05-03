package cn.solarmoon.spark_core.js.ik

// import cn.solarmoon.spark_core.js.put // Removed as per diff logic (put is used in onRegister)
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.IKComponentType
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.Function
import org.slf4j.LoggerFactory

object JSIKApi: JSApi,JSComponent() {
    override val id: String = "ik" // Name exposed to JS (e.g., IK.create(...))
    override val valueCache: MutableMap<String, String> = mutableMapOf()

    // Use SLF4J logger
    private val logger = LoggerFactory.getLogger(JSIKApi::class.java)

    // Store builders until onLoad
    // Assuming JSIKComponentTypeBuilder exists and has 'id', 'priority', and 'build()'
    private val pendingRegistrations = mutableListOf<Pair<JSIKComponentTypeBuilder, () -> Unit>>()

    
    fun create(idStr: String, configureFunc: Function) {
        val builder = JSIKComponentTypeBuilder() // Assuming this class exists
        // Store the configuration logic to run later during onLoad
        pendingRegistrations.add(builder to {
            try {
                // Set the ID first, as the configureFunc might need it implicitly
                builder.id = ResourceLocation.parse(idStr)
                // Call the JS function, passing the builder for configuration
                configureFunc.call(engine, builder) // Pass engine context and the builder
                // Configuration is done, registration will happen in onLoad
                // builder.buildAndRegister() // Removed registration call here
            } catch (e: Exception) {
                logger.error("Error configuring IKComponentType '$idStr' from JS:", e) // Use logger
            }
        })
        logger.debug("JS queued IKComponentType definition: $idStr") // Use logger
    }

    override fun onLoad() {
        logger.info("Processing ${pendingRegistrations.size} pending JS IKComponentType registrations...") // Use logger
        // Sort by priority if needed, then register
        pendingRegistrations.sortByDescending { it.first.priority } // Assuming 'priority' exists
        // pendingRegistrations.forEach { it.second.invoke() } // Removed direct invocation here
        pendingRegistrations.forEach { (builder, configureAction) ->
            try {
                configureAction.invoke() // Ensure configuration is applied
                val typeToRegister: IKComponentType? = builder.build() // Build the type instance, assuming build() exists and returns IKComponentType?
                if (typeToRegister != null) {
                    // Use the DeferredRegister pattern via SparkCore.REGISTER
                    // Assuming SparkCore.REGISTER.ikComponentType() returns a DeferredRegister<IKComponentType>
                    // And it has a build method accepting path and supplier
                    SparkCore.REGISTER.ikComponentType().build(typeToRegister.id.path) { typeToRegister }
                    logger.info("JS submitted IKComponentType for registration: ${typeToRegister.id}") // Use logger
                } else {
                     logger.error("Failed to build IKComponentType from JS definition: ${builder.id}")
                }
            } catch (e: Exception) {
                 logger.error("Error processing JS IKComponentType registration for '${builder.id ?: "unknown"}':", e)
            }
        }
        pendingRegistrations.clear()
        logger.info("Finished processing JS IKComponentType registrations.") // Use logger
    }

    override fun onReload() {
        // TODO: Decide how to handle JS-defined types on reload.
        // Option 1: Clear them (requires tracking which ones were JS-defined).
        // Option 2: Keep them (might lead to duplicates if scripts redefine).
        // Option 3: Implement a proper reload mechanism.
        logger.warn("IK component type reloading from JS not fully implemented.") // Use logger
        // For now, clear pending ones that didn't load?
        pendingRegistrations.clear()
    }
}
