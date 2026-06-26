package org.gradle.profiler.studio.domain

object ProfilerOptions {
    val profilers: List<String> = listOf(
        "async-profiler",
        "buildscan",
        "chrome-trace",
        "heap-dump",
        "jfr",
        "jprofiler",
        "yourkit",
        "yourkit-tracing",
        "yourkit-heap",
        "none",
    )

    val mutatorTypes: List<String> = listOf(
        "apply-abi-change-to",
        "apply-non-abi-change-to",
        "apply-android-layout-change-to",
        "apply-android-manifest-change-to",
        "apply-android-resource-change-to",
        "apply-android-resource-value-change-to",
        "apply-build-script-change-to",
        "apply-cpp-change-to",
        "apply-h-change-to",
        "apply-kotlin-composable-change-to",
        "apply-project-dependency-change-to",
        "apply-property-resource-change-to",
        "clear-build-cache-before",
        "clear-configuration-cache-state-before",
        "clear-dir",
        "clear-gradle-user-home-before",
        "clear-jars-cache-before",
        "clear-project-cache-before",
        "clear-transform-cache-before",
        "copy-file",
        "delete-file",
        "git-checkout",
        "git-revert",
        "show-build-cache-size",
    )
}
