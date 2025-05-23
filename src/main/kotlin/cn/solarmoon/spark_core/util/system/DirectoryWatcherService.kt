package cn.solarmoon.spark_core.util.system

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.handler.IDynamicResourceHandler
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class DirectoryWatcherService {

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val watchedDirectories: ConcurrentHashMap<WatchKey, Pair<Path, IDynamicResourceHandler>> = ConcurrentHashMap()
    private val executorService = Executors.newSingleThreadExecutor()
    @Volatile private var isRunning = false

    fun registerDirectory(directoryPath: Path, handler: IDynamicResourceHandler) {
        if (!Files.isDirectory(directoryPath)) {
            throw IllegalArgumentException("Path must be a directory: $directoryPath")
        }
        // TODO: Handle if directory is already registered
        val watchKey = directoryPath.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        )
        watchedDirectories[watchKey] = Pair(directoryPath, handler)
        println("Registered directory for watching: $directoryPath")
        // Process existing files in the directory
        processExistingFilesInDirectory(directoryPath, handler)
    }

    private fun processExistingFilesInDirectory(directoryPath: Path, handler: IDynamicResourceHandler) {
        SparkCore.LOGGER.info("Starting to process existing files in: $directoryPath for handler: ${handler.getResourceType()}")
        try {
            Files.walkFileTree(directoryPath, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) {
                        try {
                            SparkCore.LOGGER.debug("Processing existing file: $file")
                            handler.onResourceAdded(file)
                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("Error processing existing file $file with handler ${handler.getResourceType()}: ${e.message}", e)
                            // Continue processing other files
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    SparkCore.LOGGER.error("Failed to visit file $file during initial scan: ${exc.message}", exc)
                    return FileVisitResult.CONTINUE // Continue processing other files
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) {
                        SparkCore.LOGGER.error("Error after visiting directory $dir during initial scan: ${exc.message}", exc)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            SparkCore.LOGGER.error("IOException while walking file tree for $directoryPath: ${e.message}", e)
        }
        SparkCore.LOGGER.info("Finished processing existing files in: $directoryPath for handler: ${handler.getResourceType()}")
    }

    fun start() {
        if (isRunning) {
            println("Service is already running.")
            return
        }
        isRunning = true
        executorService.submit {
            println("DirectoryWatcherService started.")
            while (isRunning) {
                val key: WatchKey?
                try {
                    key = watchService.take() // Blocks until an event occurs or service is stopped
                } catch (e: InterruptedException) {
                    if (!isRunning) {
                        Thread.currentThread().interrupt()
                        break // Exit loop if service is stopped
                    }
                    continue // Continue if interrupted for other reasons
                } catch (e: ClosedWatchServiceException) {
                    if (!isRunning) {
                         println("WatchService closed as part of shutdown.")
                    } else {
                        System.err.println("WatchService closed unexpectedly. Stopping service.")
                        e.printStackTrace()
                        stopInternal() // Attempt to clean up resources
                    }
                    break
                }


                if (key == null) {
                    if(isRunning) { // If key is null but service should be running, something is wrong.
                        System.err.println("WatchKey is null, but service is running. This might indicate an issue. Continuing...")
                    }
                    continue
                }


                val (watchedPath, handler) = watchedDirectories[key] ?: run {
                    System.err.println("WatchKey not recognized: ${key.watchable()}")
                    key.cancel() // Important to cancel keys no longer in use
                    return@submit // Should not happen if map is managed correctly
                }

                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        System.err.println("OVERFLOW event occurred for ${watchedPath}. Some events might have been lost.")
                        continue
                    }

                    @Suppress("UNCHECKED_CAST")
                    val eventPath = event.context() as Path
                    val fullPath = watchedPath.resolve(eventPath)

                    when (kind) {
                        StandardWatchEventKinds.ENTRY_CREATE -> {
                            println("File created: $fullPath")
                            handler.onResourceAdded(fullPath)
                        }
                        StandardWatchEventKinds.ENTRY_MODIFY -> {
                            println("File modified: $fullPath")
                            handler.onResourceModified(fullPath)
                        }
                        StandardWatchEventKinds.ENTRY_DELETE -> {
                            println("File deleted: $fullPath")
                            handler.onResourceRemoved(fullPath)
                        }
                    }
                }

                val valid = key.reset()
                if (!valid) {
                    println("WatchKey for $watchedPath is no longer valid. Removing from watch list.")
                    watchedDirectories.remove(key)
                    // Potentially, re-register if desired, or notify handler of directory becoming inaccessible
                    if (watchedDirectories.isEmpty() && isRunning) {
                        println("No more directories to watch. Consider stopping the service if this is not expected.")
                    }
                }
            }
            println("DirectoryWatcherService event loop finished.")
            cleanup() // Ensure cleanup when loop exits
        }
    }
    
    private fun stopInternal() {
        if (!isRunning) return
        isRunning = false
        try {
            watchService.close() // This will interrupt the watchService.take() call
        } catch (e: Exception) {
            System.err.println("Error closing WatchService: ${e.message}")
            e.printStackTrace()
        }
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
        println("DirectoryWatcherService stopped and resources cleaned up.")
    }


    fun stop() {
        if (!isRunning) {
            println("Service is not running.")
            return
        }
        println("Attempting to stop DirectoryWatcherService...")
        stopInternal()
    }

    private fun cleanup() {
        println("Cleaning up resources...")
        watchedDirectories.keys.forEach { key ->
            try {
                key.cancel()
            } catch (e: Exception) {
                System.err.println("Error cancelling watch key during cleanup: ${e.message}")
            }
        }
        watchedDirectories.clear()
        println("All watched directories cleared.")
        // watchService and executorService are handled in stopInternal
    }

    // Optional: Method to unregister a specific directory
    fun unregisterDirectory(directoryPath: Path) {
        var keyToUnregister: WatchKey? = null
        for ((key, value) in watchedDirectories) {
            if (value.first == directoryPath) {
                keyToUnregister = key
                break
            }
        }

        keyToUnregister?.let {
            it.cancel()
            watchedDirectories.remove(it)
            println("Unregistered directory: $directoryPath")
        } ?: println("Directory not found in watch list: $directoryPath")
    }
}
