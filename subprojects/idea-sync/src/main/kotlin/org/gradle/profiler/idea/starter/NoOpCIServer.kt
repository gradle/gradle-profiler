package org.gradle.profiler.idea.starter

import com.intellij.ide.starter.ci.CIServer
import java.nio.file.Path

/**
 * By default, Starter uses [com.intellij.ide.starter.ci.NoCIServer] implementation, which spoils the output with redundant logs.
 */
internal object NoOpCIServer : CIServer {
    override val isBuildRunningOnCI: Boolean = false
    override val buildNumber: String = ""
    override val branchName: String = ""
    override val buildParams: Map<String, String> = emptyMap()

    override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {}

    override fun reportTestFailure(
        testName: String,
        message: String,
        details: String,
        linkToLogs: String?
    ) {
    }

    override fun ignoreTestFailure(testName: String, message: String, details: String?) {}

    override fun isTestFailureShouldBeIgnored(message: String): Boolean = false
}
