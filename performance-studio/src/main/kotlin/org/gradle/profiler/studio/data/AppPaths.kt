package org.gradle.profiler.studio.data

import java.io.File

object AppPaths {
    private const val APP_NAME = "GradlePerformanceStudio"

    val dataDir: File by lazy {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            os.contains("mac") -> home.resolve("Library/Application Support/$APP_NAME")
            os.contains("win") -> File(System.getenv("APPDATA") ?: home.absolutePath).resolve(APP_NAME)
            else -> {
                val xdg = System.getenv("XDG_DATA_HOME")
                val base = if (xdg.isNullOrBlank()) home.resolve(".local/share") else File(xdg)
                base.resolve(APP_NAME)
            }
        }
        dir.apply { mkdirs() }
    }

    val databaseFile: File get() = dataDir.resolve("studio.db")
    val runsDir: File get() = dataDir.resolve("runs").apply { mkdirs() }
}
