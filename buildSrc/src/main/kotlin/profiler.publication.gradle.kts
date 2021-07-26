import java.net.URI

plugins {
    id("maven-publish")
    id("signing")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "GradleBuildInternal"
            url = gradleInternalRepositoryUrl()
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}


fun Project.gradleInternalRepositoryUrl(): URI {
    val isSnapshot = property("profiler.version").toString().endsWith("-SNAPSHOT")
    val repositoryQualifier = if (isSnapshot) "snapshots" else "releases"
    return uri("https://repo.gradle.org/gradle/ext-$repositoryQualifier-local")
}

val signArtifacts: Boolean = System.getenv("PGP_SIGNING_KEY").isNotEmpty()
tasks.withType<Sign>().configureEach { isEnabled = signArtifacts }
signing {
    useInMemoryPgpKeys(
        System.getenv("PGP_SIGNING_KEY"),
        System.getenv("PGP_SIGNING_KEY_PASSPHRASE")
    )
    publishing.publications.configureEach {
        if (signArtifacts) {
            signing.sign(this)
        }
    }
}

