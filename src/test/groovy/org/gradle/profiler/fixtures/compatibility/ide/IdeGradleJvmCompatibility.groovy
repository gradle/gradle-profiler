package org.gradle.profiler.fixtures.compatibility.ide

import groovy.json.JsonSlurper
import org.gradle.profiler.ide.IdeType

import java.util.jar.JarFile

/**
 * Parses the Gradle JVM compatibility matrix bundled inside an IDE installation
 * (from plugins/gradle/lib/gradle.jar → compatibility/compatibility.json).
 *
 * Used to check if a given Java version is supported by the IDE,
 * so tests can downgrade the JVM when needed.
 */
class IdeGradleJvmCompatibility {

    private final Set<Integer> supportedJavaVersions

    private IdeGradleJvmCompatibility(Set<Integer> supportedJavaVersions) {
        this.supportedJavaVersions = supportedJavaVersions
    }

    static IdeGradleJvmCompatibility fromIdeHome(IdeType ide, File ideHome) {
        def gradleJar = findGradlePluginJar(ideHome)
        if (gradleJar == null) {
            throw new IllegalStateException("Can't find gradle.jar in ${ide.displayName} distribution at: $ideHome")
        }
        def json = extractCompatibilityJson(gradleJar)
        if (json == null) {
            throw new IllegalStateException("Can't find compatibility.json in ${ide.displayName} distribution at: $ideHome")
        }
        return parse(json)
    }

    Set<Integer> getSupportedJavaVersions() {
        return supportedJavaVersions
    }

    private static File findGradlePluginJar(File ideHome) {
        def candidates = [
            new File(ideHome, "Contents/plugins/gradle/lib/gradle.jar"),             // macOS .app (pre-2026.1)
            new File(ideHome, "plugins/gradle/lib/gradle.jar"),                      // Linux/Windows (pre-2026.1)
            new File(ideHome, "Contents/plugins/gradle-plugin/lib/gradle-plugin.jar"), // macOS .app (2026.1+)
            new File(ideHome, "plugins/gradle-plugin/lib/gradle-plugin.jar"),          // Linux/Windows (2026.1+)
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

    private static IdeGradleJvmCompatibility parse(String json) {
        def entries = new JsonSlurper().parseText(json) as List<Map>
        def latestEntry = entries.findAll { it.containsKey("supportedJavaVersions") }.last()
        def versions = (latestEntry.supportedJavaVersions as List<String>).collect { it as int }.toSet()
        return new IdeGradleJvmCompatibility(versions)
    }
}
