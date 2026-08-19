package org.gradle.profiler.studio

import java.io.File

object ProfilerHome {
    fun locate(): File? {
        System.getProperty("studio.profilerHome")?.let { path ->
            val dir = File(path)
            if (dir.resolve("bin/gradle-profiler").canExecute()) return dir
        }
        val codeSource = ProfilerHome::class.java.protectionDomain?.codeSource?.location?.toURI()?.let(::File)
        var cursor: File? = codeSource
        while (cursor != null) {
            val candidate = cursor.resolve("Resources/gradle-profiler/bin/gradle-profiler")
            if (candidate.canExecute()) return candidate.parentFile.parentFile
            cursor = cursor.parentFile
        }
        return null
    }
}
