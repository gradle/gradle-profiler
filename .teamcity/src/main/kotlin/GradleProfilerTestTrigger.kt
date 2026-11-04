import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ReuseBuilds
import jetbrains.buildServer.configs.kotlin.triggers.vcs

class GradleProfilerTestTrigger(testBuilds: List<BuildType>) : BuildType({
    name = "Tests (Trigger)"
    type = Type.COMPOSITE

    gradleProfilerVcs()
    triggers {
        vcs {
            branchFilter = """
                +:<default>
                +:devprod/*
                -:dependabot/*
                +pr: github_role=MEMBER
                -pr: source=dependabot/*
            """.trimIndent()
        }
    }

    features {
        discoverPullRequests()
    }

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
