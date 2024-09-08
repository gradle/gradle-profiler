package org.gradle.profiler.ide

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.ide.installer.IdeInstallerFile
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

class RunIdeStarterImpl : RunIdeStarter {
    override fun newContext(projectLocation: String, ideLocation: String): RunIdeContext {
        val testVersion = "2023.2.3"
        val ideInfo = IdeInfo(
            productCode = "IC",
            version = "2023.2",
//            buildNumber = testVersion,
            executableFileName = "idea",
            fullName = "IntelliJ IDEA Community",
            platformPrefix = "idea",
            // getInstaller = { _ -> ExistingIdeInstaller(Paths.get(ideLocation)) }
        )
        val testCase = TestCase(
            ideInfo,
            LocalProjectInfo(Paths.get(projectLocation)),
        )
        val context = Starter.newContext("test", testCase)
        return RunIdeContextImpl(context)
    }

    class ExistingIdeInstaller(private val installedIdePath: Path) : IdeInstaller {
        override fun install(ideInfo: IdeInfo, includeRuntimeModuleRepository: Boolean): Pair<String, InstalledIde> {
            val ideInstaller = IdeInstallerFile(installedIdePath, "locally-installed-ide")
            val installDir = GlobalPaths.instance
                .getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"
            installDir.toFile().deleteRecursively()
            val installedIde = installedIdePath.toFile()
            val destDir = installDir.resolve(installedIdePath.name).toFile()
            if (SystemInfo.isMac) {
                ProcessExecutor("copy app", null, 5.minutes, emptyMap(), listOf("ditto", installedIde.absolutePath, destDir.absolutePath)).start()
            }
            else {
                FileUtil.copyDir(installedIde, destDir)
            }
            return Pair(
                ideInstaller.buildNumber,
                IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName)
            )
        }
    }

    private object Starter {
        private fun newTestContainer(): TestContainer<*> {
            return TestContainerImpl()
        }

        fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false): IDETestContext =
            newTestContainer().initializeTestContext(
                testName = testName,
                testCase = testCase,
                preserveSystemDir = preserveSystemDir
            )
    }
}
