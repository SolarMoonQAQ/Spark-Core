package cn.solarmoon.spark_core.resource.autoregistry

/**
 * Marks an IDynamicResourceHandler implementation for automatic discovery and registration.
 * Classes annotated with this must implement the IDynamicResourceHandler interface.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRegisterHandler
