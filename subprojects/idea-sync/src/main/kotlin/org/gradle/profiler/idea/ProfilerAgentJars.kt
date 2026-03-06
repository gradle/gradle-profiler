package org.gradle.profiler.idea

import java.nio.file.Path

data class ProfilerAgentJars(
    val agentJar: Path,
    val instrumentationSupportJar: Path,
    val asmJar: Path,
    val protocolJar: Path
)
