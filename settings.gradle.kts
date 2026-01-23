plugins {
    id("com.gradle.develocity").version("4.1")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.3")
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.10.0")
}

rootProject.name = "gradle-profiler"

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        maven {
            name = "Gradle public repository"
            url = uri("https://repo.gradle.org/gradle/libs-releases")
            content {
                includeModule("org.gradle", "gradle-tooling-api")
            }
        }

        // see profiler.android-studio-setup.gradle.kts
        listOf(
            // Urls of Android Studio archive
            "https://redirector.gvt1.com/edgedl/android/studio/ide-zips",
            "https://redirector.gvt1.com/edgedl/android/studio/install"
        ).forEach {
            ivy {
                url = uri(it)
                patternLayout {
                    artifact("[revision]/[artifact]-[revision]-[ext]")
                }
                metadataSources { artifact() }
                content {
                    includeGroup("android-studio")
                }
            }
        }

        // Declare the Node.js download repository for the Node plugin
        // https://github.com/node-gradle/gradle-node-plugin/blob/7.1.0/docs/faq.md#is-this-plugin-compatible-with-centralized-repositories-declaration
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }

        mavenCentral()
    }

    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

include("chrome-trace")
include("build-operations")
include("heap-dump")
include("client-protocol")
include("instrumentation-support")
include("studio-agent")
include("studio-plugin")
include("build-action")
include("scenario-definition")
include("tooling-action")

rootProject.children.forEach {
    it.projectDir = rootDir.resolve( "subprojects/${it.name}")
}
