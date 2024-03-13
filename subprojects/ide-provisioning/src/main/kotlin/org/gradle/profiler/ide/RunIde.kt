package org.gradle.profiler.ide

import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.importGradleProject
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import java.nio.file.Path
import java.nio.file.Paths

class RunIde {

    fun runIde(projectLocation: String, buildVersion: String, ideLocation: String) {
        val testVersion = "2021.2.2"
        val ideInfo = IdeInfo(
            productCode = "IC",
            version = "2023.2.3",
            buildNumber = buildVersion,
            executableFileName = "idea",
            fullName = "IntelliJ IDEA Community",
            platformPrefix = "idea",
            getInstaller = { _ -> IdeLocalInstaller(Paths.get(ideLocation)) }
        )
        val testCase = TestCase(
            ideInfo,
            LocalProjectInfo(Paths.get(projectLocation)),
        )
        val context = Starter.newContext("test", testCase)
            .applyVMOptionsPatch {
                removeProfilerAgents()

            }

        context.runIDE(commands = CommandChain().importGradleProject().waitForSmartMode().importGradleProject())
        println("Waiting for 10 seconds")
        Thread.sleep(10_000)
        context.runIDE(commands = CommandChain().importGradleProject().waitForSmartMode().exitApp())
    }

    class IdeLocalInstaller(private val installedLocation: Path) : IdeInstaller {
        override fun install(ideInfo: IdeInfo, includeRuntimeModuleRepository: Boolean): Pair<String, InstalledIde> {
            return ideInfo.buildNumber to IdeDistributionFactory.installIDE(installedLocation.toFile().parentFile, ideInfo.executableFileName)
        }
    }
}
