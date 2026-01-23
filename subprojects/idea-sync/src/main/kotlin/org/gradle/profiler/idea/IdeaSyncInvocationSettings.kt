package org.gradle.profiler.idea

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import java.nio.file.Path
import joptsimple.OptionParser
import joptsimple.OptionSet
import joptsimple.util.PathConverter

private const val CLI_IDEA_VERSION = "idea-version"
private const val CLI_IDEA_HOME = "idea-home"
private const val CLI_IDEA_SANDBOX = "idea-sandbox"

class IdeaSyncInvocationSettings(
    val ideaVersion: String,
    val ideaHome: Path,
    val ideaSandbox: Path?
) {
    internal val ideInfo: IdeInfo
        get() {
            val versionComponents = ideaVersion.split("-")
            return IdeProductProvider.IC.copy(
                version = versionComponents[0],
                buildType = versionComponents.getOrNull(1) ?: "release"
            )
        }

    companion object {
        @JvmStatic
        fun registerCliOptions(parser: OptionParser) {
            with(parser) {
                accepts(CLI_IDEA_VERSION, "Version of IntelliJ IDEA").withRequiredArg()
                accepts(CLI_IDEA_HOME, "Path to store IDEA installers and executables")
                    .withRequiredArg()
                    .withValuesConvertedBy(PathConverter())
                accepts(CLI_IDEA_SANDBOX, "Path to store IDE launch artifacts like logs")
                    .withRequiredArg()
                    .withValuesConvertedBy(PathConverter())
            }
        }

        @JvmStatic
        fun ofParsedOptions(options: OptionSet): IdeaSyncInvocationSettings? = with(options) {
            if (has(CLI_IDEA_HOME) && has(CLI_IDEA_VERSION)) {
                IdeaSyncInvocationSettings(
                    ideaVersion = valueOf(CLI_IDEA_VERSION) as String,
                    ideaHome = valueOf(CLI_IDEA_HOME) as Path,
                    ideaSandbox = valueOf(CLI_IDEA_SANDBOX) as? Path
                )
            } else {
                null
            }
        }
    }
}



