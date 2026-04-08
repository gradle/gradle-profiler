package org.gradle.profiler.fixtures.compatibility.ide

import groovy.json.JsonSlurper
import org.gradle.api.JavaVersion
import org.gradle.profiler.studio.tools.IntellijFinder

import java.util.jar.JarFile

/**
 * Parses the Gradle JVM compatibility matrix bundled inside an IDE installation
 * (from plugins/gradle/lib/gradle.jar → compatibility/compatibility.json).
 *
 * Used to check if a given Java version is supported by the IDE,
 * so tests can downgrade the JVM when needed.
 */
class IntellijGradleJvmCompatibility {

    private final Set<Integer> supportedJavaVersions

    private IntellijGradleJvmCompatibility(Set<Integer> supportedJavaVersions) {
        this.supportedJavaVersions = supportedJavaVersions
    }

    static IntellijGradleJvmCompatibility fromIdeHome(File ideHome) {
        def gradleJar = findGradlePluginJar(ideHome)
        if (gradleJar == null) {
            return null
        }
        def json = extractCompatibilityJson(gradleJar)
        if (json == null) {
            return null
        }
        return parse(json)
    }

    Set<Integer> getSupportedJavaVersions() {
        return supportedJavaVersions
    }

    private static File findGradlePluginJar(File ideHome) {
        def candidates = [
            new File(ideHome, "Contents/plugins/gradle/lib/gradle.jar"), // macOS .app
            new File(ideHome, "plugins/gradle/lib/gradle.jar"),          // Linux/Windows
        ]
        return candidates.find { it.isFile() }
    }

    private static String extractCompatibilityJson(File jarFile) {
        def jar = new JarFile(jarFile)
        try {
            def entry = jar.getEntry("compatibility/compatibility.json")
            if (entry == null) {
                return null
            }
            return jar.getInputStream(entry).text
        } finally {
            jar.close()
        }
    }

    private static IntellijGradleJvmCompatibility parse(String json) {
        def entries = new JsonSlurper().parseText(json) as List<Map>
        def latestEntry = entries.findAll { it.containsKey("supportedJavaVersions") }.last()
        def versions = (latestEntry.supportedJavaVersions as List<String>).collect { it as int }.toSet()
        return new IntellijGradleJvmCompatibility(versions)
    }
}
