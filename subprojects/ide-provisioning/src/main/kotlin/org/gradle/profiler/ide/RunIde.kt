package org.gradle.profiler.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.installer.IdeInstallerFile
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.InstallerGlobalPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.importGradleProject
import java.nio.file.Path
import java.nio.file.Paths

class RunIde {

    fun runIde(projectLocation: String, ideLocation: String) {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(Paths.get(projectLocation))
        )
        val ideInfo = IdeInfo(
            productCode = "IC",
            version = "2021.2.2",
            buildNumber = "212.5284.40",
            executableFileName = "idea",
            fullName = "IntelliJ IDEA Community",
            platformPrefix = "idea",
            getInstaller = { _ -> IdeLocalInstaller(Paths.get(ideLocation)) }
        )
        val context = Starter.newContext("test", testCase)
            .prepareProjectCleanImport()

        context.runIDE(commands = CommandChain().importGradleProject().exitApp())
    }

    class IdeLocalInstaller(private val installer: Path) : IdeInstaller {
        override fun install(ideInfo: IdeInfo, includeRuntimeModuleRepository: Boolean): Pair<String, InstalledIde> {
            val ideInstaller = IdeInstallerFile(installer, "locally-installed-ide")
            val installDir = InstallerGlobalPaths()
                .getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"
            FileUtils.deleteDirectory(installDir.toFile())
            FileUtils.copyDirectory(installer.toFile(), installDir.toFile())
            return Pair(
                ideInstaller.buildNumber,
                IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName)
            )
        }
    }
}
