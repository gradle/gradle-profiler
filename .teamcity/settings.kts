import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.1"

project {
    description = "Runs tests and integration tests of the Gradle Profiler (https://github.com/gradle/gradle-profiler)"

    vcsRoot(VersionedSettings_1)

    buildType(MacOSJava18)
    buildType(WindowsJava18)
    buildType(GradleProfilerPublishing)
    buildType(LinuxJava18)
}

object GradleProfilerPublishing : BuildType({
    name = "Gradle profiler Publishing"
    description = "Publish Gradle profiler Gradle's Artifactory repository"

    artifactRules = "build/reports/** => reports"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        text("ARTIFACTORY_USERNAME", "bot-build-tool", allowEmpty = true)
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:2b7529cd-77cd-49f4-9416-9461f6ac9018", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean publishToGradleBuildInternal"
            buildFile = ""
            gradleParams = "-PartifactoryUsername=%ARTIFACTORY_USERNAME% -PartifactoryPassword=%ARTIFACTORY_PASSWORD%"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object LinuxJava18 : BuildType({
    name = "Linux - Java 1.8"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
            gradleParams = "-s"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:*
                -:pull/*
            """.trimIndent()
        }
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:7c8c64ad-776d-4469-8400-5618da5de337"
                }
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object MacOSJava18 : BuildType({
    name = "MacOS - Java 1.8"

    params {
        param("env.JAVA_HOME", "%macos.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
            gradleParams = "-s"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:*
                -:pull/*
            """.trimIndent()
        }
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:7c8c64ad-776d-4469-8400-5618da5de337"
                }
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac OS X")
    }
})

object WindowsJava18 : BuildType({
    name = "Windows - Java 1.8"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
            gradleParams = "-s"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:*
                -:pull/*
            """.trimIndent()
        }
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:7c8c64ad-776d-4469-8400-5618da5de337"
                }
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})

object VersionedSettings_1 : GitVcsRoot({
    id("VersionedSettings")
    name = "Gradle Profiler Versioned Settings"
    url = "git@github.com:gradle/gradle-profiler.git"
    branch = "teamcity-versioned-settings"
    authMethod = uploadedKey {
        uploadedKey = "id_rsa_gradlewaregitbot"
    }
})
