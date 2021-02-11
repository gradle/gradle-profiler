import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ReuseBuilds
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

class GradleProfilerTestTrigger(testBuilds: List<BuildType>) : BuildType({
    name = "Tests (Trigger)"
    type = Type.COMPOSITE

    gradleProfilerVcs()
    triggers {
        vcs {
            branchFilter = """
                +:*
                -:pull/*
            """.trimIndent()
        }
    }

    agentRequirement(Os.linux)

    dependencies {
        testBuilds.forEach {
            snapshot(it) {
                reuseBuilds = ReuseBuilds.SUCCESSFUL
                onDependencyCancel = FailureAction.CANCEL
                onDependencyFailure = FailureAction.ADD_PROBLEM
            }
        }
    }
})
