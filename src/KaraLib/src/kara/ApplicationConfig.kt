package kara

import kara.config.Config
import java.io.File
import java.net.URL
import java.util.*

/**
 * Store application configuration.
 */
open class ApplicationConfig(val appClassloader: ClassLoader) : Config() {

    companion object {
        fun loadFrom(configPath: String, classLoader: ClassLoader? = null): ApplicationConfig {
            val config = ApplicationConfig(classLoader ?: ApplicationConfig::class.java.classLoader!!)
            Config.readConfig(config, configPath, config.appClassloader)
            return config
        }
    }

    val isMinifyCss by lazy { tryGet("kara.minify.css")?.toBoolean() ?: false }
    val isMinifyJs by lazy { tryGet("kara.minify.js")?.toBoolean() ?: false }

    val isReloadClasses by lazy { tryGet("kara.reloadClasses")?.toBoolean() ?: false }

    /** Returns true if the application is running in the development environment. */
    fun isDevelopment(): Boolean = get("kara.environment") == "development"

    /** Returns true if the application is running in the test environment. */
    fun isTest(): Boolean = get("kara.environment") == "test"

    /** Returns true if the application is running in the production environment. */
    fun isProduction(): Boolean = get("kara.environment") == "production"

    val applicationPackageName: String
        get() = this["kara.appPackage"]

    private val _publicDirectories by lazy {
        readPublicDirProperty().mapNotNull {
            val dirPath = it.trim()
            val dir = File(dirPath)
            val context = ActionContext.current().request.servletContext
            if ((dir.parent == null || !dir.isDirectory) && context != null) {
                logger.info("Can't find public dir $dirPath. Trying to resolve it via servlet context.")
                return@mapNotNull context.getRealPath(dirPath)?.let { path ->
                    if (File(path).isDirectory) {
                        path
                    } else {
                        logger.warn("Resolved path is not directory $path")
                        null
                    }
                }
            }
            it
        }
    }
    /** Directories where publicly available files (like stylesheets, scripts, and images) will go. */
    val publicDirectories: List<String>
        get() = ActionContext.tryGet()?.let { _publicDirectories } ?: readPublicDirProperty()

    private fun readPublicDirProperty() = tryGet("kara.publicDir")?.split(';')?.filter { it.isNotBlank() }.orEmpty()

    internal val routePackages: List<String>
        get() = tryGet("kara.routePackages")?.split(',')?.toList()?.map { it.trim() }
                ?: listOf("$applicationPackageName.routes", "$applicationPackageName.styles")


    /** The port to run the server on. */
    val port: String
        get() = tryGet("kara.port") ?: "8080"

    fun classPath(ctx: String): Array<URL> {
        val urls = ArrayList<URL>()
        val key = if (ctx.isBlank()) "kara.classpath" else "kara.classpath.$ctx"
        tryGet(key)?.let {
            urls.addAll(it.split(':')
                    .flatMap {
                        when {
                            it.endsWith("/**") -> {
                                val answer = ArrayList<File>()
                                File(it.removeSuffix("/**")).walkTopDown().forEach { file -> if (file.isFile && file.name.endsWith(".jar")) answer.add(file) }
                                answer
                            }

                            it.endsWith("/*") -> {
                                File(it.removeSuffix("/*")).listFiles{ file -> file.isFile && file.name.endsWith(".jar") }?.toList() ?: listOf()
                            }

                            else -> {
                                listOf(File(it))
                            }
                        }
                    }
                    .map { it.toURI().toURL() })
        }
        return urls.toTypedArray()
    }
}
