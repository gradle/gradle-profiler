package org.gradle.profiler.studio.introspection

import org.gradle.profiler.studio.ProfilerHome
import java.net.URLClassLoader

object ScenarioOptionRegistry {

    val profilers: List<String> by lazy { loadProfilers() }
    val mutatorTypes: List<String> by lazy { loadMutatorTypes() }

    private val classLoader: ClassLoader? by lazy {
        val home = ProfilerHome.locate() ?: return@lazy null
        val libDir = home.resolve("lib")
        val jars = libDir.listFiles { f -> f.extension == "jar" } ?: return@lazy null
        if (jars.isEmpty()) return@lazy null
        URLClassLoader(
            jars.map { it.toURI().toURL() }.toTypedArray(),
            ScenarioOptionRegistry::class.java.classLoader,
        )
    }

    private fun loadProfilers(): List<String> {
        val cl = classLoader ?: return DEFAULT_PROFILERS
        return runCatching {
            val cls = cl.loadClass("org.gradle.profiler.ProfilerFactory")
            @Suppress("UNCHECKED_CAST")
            val names = cls.getMethod("getAvailableProfilers").invoke(null) as Set<String>
            (names + "none").distinct().sorted()
        }.getOrDefault(DEFAULT_PROFILERS)
    }

    private fun loadMutatorTypes(): List<String> {
        val cl = classLoader ?: return DEFAULT_MUTATORS
        return runCatching {
            val cls = cl.loadClass("org.gradle.profiler.ScenarioLoader")
            val field = cls.getDeclaredField("BUILD_MUTATOR_CONFIGURATORS").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val map = field.get(null) as Map<String, *>
            map.keys.sorted()
        }.getOrDefault(DEFAULT_MUTATORS)
    }

    private val DEFAULT_PROFILERS = listOf("async-profiler", "jfr", "none")
    private val DEFAULT_MUTATORS = listOf("apply-abi-change-to", "apply-non-abi-change-to")
}
