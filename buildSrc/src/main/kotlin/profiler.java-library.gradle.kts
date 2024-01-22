import org.gradle.internal.os.OperatingSystem

plugins {
    id("profiler.allprojects")
    id("java-library")
    id("me.champeau.mrjar")
}

repositories {
    maven {
        name = "Gradle public repository"
        url = uri("https://repo.gradle.org/gradle/libs-releases")
        content {
            includeModule("org.gradle", "gradle-tooling-api")
        }
    }
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }

    mavenCentral()
}

multiRelease {
    targetVersions(8, 17)
}

//val java17 by sourceSets.creating {
//    java.srcDirs(sourceSets.main.get().java)
//}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

//    registerFeature("java17") {
//        usingSourceSet(java17)
//        capability(project.group.toString(), "ide-provisioning-java17", project.version.toString())
//    }
}

//configurations {
//    val mainImplementation = implementation.get()
//
//    getByName(java17.implementationConfigurationName) {
//        dependencies.addAllLater(provider { mainImplementation.dependencies })
//        dependencyConstraints.addAllLater(provider { mainImplementation.dependencyConstraints })
//    }
//}

tasks.withType<Test>().configureEach {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
    useJUnitPlatform()
}
