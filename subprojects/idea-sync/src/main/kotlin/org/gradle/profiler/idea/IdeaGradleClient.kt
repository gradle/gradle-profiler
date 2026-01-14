package org.gradle.profiler.idea

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.plugins.gradle.ImportGradleProjectUtil
import com.intellij.driver.sdk.setupOrDetectSdk
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import kotlinx.coroutines.*
import org.gradle.profiler.GradleClient
import org.gradle.profiler.client.protocol.Server
import org.gradle.profiler.client.protocol.ServerConnection
import org.gradle.profiler.client.protocol.messages.GradleInvocationParameters
import org.gradle.profiler.client.protocol.messages.StudioAgentConnectionParameters
import org.gradle.profiler.idea.starter.DefaultIdeInstallerFactory
import org.gradle.profiler.idea.starter.NoOpCIServer
import org.gradle.profiler.result.BuildActionResult
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class IdeaGradleClient(
    private val scenarioDefinition: IdeaSyncScenarioDefinition,
    private val invocationSettings: IdeaSyncInvocationSettings,
    private val profilerAgentJars: ProfilerAgentJars,
    private val gradleHome: File,
    private val projectDir: Path,
    private val cleanCacheMode: CleanCacheMode
) : GradleClient {

    enum class CleanCacheMode {
        BEFORE_BUILD,
        BEFORE_SCENARIO
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var ideaRun: BackgroundRun? = null
    private var profilerAgentConnection: ServerConnection? = null

    private var syncOrdinal = 0

    init {
        val ideaHome = invocationSettings.ideaHome
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) {
                NoOpCIServer
            }
            bindSingleton<IdeInstallerFactory>(overrides = true) {
                DefaultIdeInstallerFactory((ideaHome / "installers").createDirectories())
            }
            bindSingleton<GlobalPaths>(overrides = true) {
                object : GlobalPaths(ideaHome) {
                    override val testHomePath: Path
                        get() = invocationSettings.ideaSandbox ?: ideaHome
                    override val localCacheDirectory: Path
                        get() = ideaHome
                }
            }
        }
    }

    internal fun sync(gradleArgs: List<String>, jvmArgs: List<String>): BuildActionResult {
        if (cleanCacheMode == CleanCacheMode.BEFORE_BUILD) {
            // Closing IDETestContext and create a fresh one is an equivalent of "Invalidate Caches And Restart"
            closeIde()
        }
        return ideaRun?.driver?.let { driver ->
            profilerAgentConnection?.let { agentConnection ->
                driver.importProjectMeasured(agentConnection, gradleArgs, jvmArgs)
            } ?: error("Profiler agent connection must be established")
        } ?: firstSync(gradleArgs, jvmArgs)
    }

    private fun firstSync(gradleArgs: List<String>, jvmArgs: List<String>): IdeaBuildActionResult {
        require(ideaRun == null) { "Stale IDEA run found" }
        require(profilerAgentConnection == null) { "Stale Profiler Agent connection found" }

        val testCase = TestCase(
            invocationSettings.ideInfo,
            LocalProjectInfo(projectDir)
        )
        val testContext = Starter.newContext("Profile IDEA Sync", testCase)
        val profilerAgentServer = Server("agent")

        profilerLog("Launching IDEA ${invocationSettings.ideaVersion}")
        ideaRun = testContext
            // Indexing takes time and memory
            .skipIndicesInitialization()
            .disableAutoImport()
            .addProfilerAgent(profilerAgentJars, profilerAgentServer.port)
            .applyScenarioDefinition(scenarioDefinition)
            // TODO Uncomment when start to use FUS events instead manual measuring
            // This dir keeps sync statistics, we want it to be clean on each run
//            .wipeEventLogDataDir()
            .applyVMOptionsPatch {
                addSystemProperty("gradle.compatibility.update.interval", 0)

                // TODO Uncomment when start to use FUS events instead manual measuring
                // Internal test mode supposes flushing FUS events fired each 10 seconds.
                // We use FUS report for extracting sync duration data
//                addSystemProperty("idea.is.internal", "true")
//                addSystemProperty("fus.internal.test.mode", "true")
            }
            .runIdeWithDriver {
                addVMOptionsPatch {
                    clearSystemProperty("ide.performance.screenshot")
                }
            }

        profilerAgentConnection = profilerAgentServer.waitForIncoming(ofMinutes(2)).apply {
            send(StudioAgentConnectionParameters(gradleHome))
        }

        val sdk = JdkDownloaderFacade.jdk17.toSdk()
        return ideaRun!!.driver.withContext {
            waitForProjectOpen()
            setupOrDetectSdk(singleProject(), sdk.sdkName, sdk.sdkType, sdk.sdkPath.toString())
            importProjectMeasured(profilerAgentConnection!!, gradleArgs, jvmArgs)
        }
    }

    private fun IDETestContext.addProfilerAgent(
        profilerAgentJars: ProfilerAgentJars,
        profilerAgentPort: Int
    ): IDETestContext {
        val agentJar = profilerAgentJars.agentJar
        val supportJar = profilerAgentJars.instrumentationSupportJar
        val asmJar = profilerAgentJars.asmJar
        val protocolJar = profilerAgentJars.protocolJar
        return applyVMOptionsPatch {
            addLine("-javaagent:$agentJar=$profilerAgentPort,$supportJar")
            addLine("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
            addLine("-Xbootclasspath/a:${listOf(asmJar, protocolJar).joinToString(File.pathSeparator)}")
        }
    }

    private fun IDETestContext.applyScenarioDefinition(scenarioDefinition: IdeaSyncScenarioDefinition): IDETestContext =
        applyVMOptionsPatch {
            scenarioDefinition.ideaJvmArgs.forEach { arg ->
                addLine(arg)
            }
        }

    override fun close() {
        closeIde()
        coroutineScope.cancel()
        syncOrdinal = 0
    }

    private fun closeIde() {
        ideaRun?.let {
            if (it.process.isAlive) {
                it.closeIdeAndWait(takeScreenshot = false)
            }
        }
        profilerAgentConnection?.close()
        ideaRun = null
        profilerAgentConnection = null
    }

    private fun Driver.importProjectMeasured(
        profilerAgentConnection: ServerConnection,
        gradleArgs: List<String>,
        jvmArgs: List<String>,
    ): IdeaBuildActionResult {
        val start = Instant.now()

        val gradleExecutionDuration = coroutineScope.async {
            profilerAgentConnection.receiveGradleInvocationStarted(Duration.ofSeconds(5))
            profilerAgentConnection.send(GradleInvocationParameters(gradleArgs, jvmArgs))
            Duration.ofMillis(profilerAgentConnection.receiveGradleInvocationCompleted(Duration.ofHours(1)).durationMillis)
        }

        syncOrdinal++
        val project = singleProject()
        utility(ImportGradleProjectUtil::class).importProject(project)
        val gradleExecutionTime = runBlocking { gradleExecutionDuration.await() }
        val noIndicatorsSince = waitForImportFinished(
            project,
            ofMinutes(10),
            Duration.ofSeconds(2),
        )
        val totalTime = Duration.between(start, noIndicatorsSince)
        return IdeaBuildActionResult(gradleExecutionTime, totalTime)
    }

    private fun profilerLog(message: String) {
        println()
        println("* $message")
        println()
    }
}
