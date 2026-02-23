plugins {
    id("profiler.allprojects")
    id("java")
    id("maven-publish")
    id("signing")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.register("checkDescription") {
    val description = provider { project.description }
    val projectPath = project.path
    doFirst {
        check(description.orNull != null) { "You must set the description of published project '$projectPath'" }
    }
}
tasks.named("sanityCheck").configure { dependsOn("checkDescription") }

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(provider { "${this@register.groupId}:${this@register.artifactId}" })
                description.set(provider {
                    require(project.description != null) { "You must set the description of published project ${project.name}" }
                    project.description
                })
                url.set("https://github.com/gradle/gradle-profiler")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name = "The Gradle team"
                        organization = "Gradle Technologies"
                        organizationUrl = "https://gradle.org"
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/gradle/gradle-profiler.git")
                    developerConnection.set("scm:git:ssh://github.com:gradle/gradle-profiler.git")
                    url.set("https://github.com/gradle/gradle-profiler/tree/master")
                }
            }

        }
    }
}

val pgpSigningKey: Provider<String> = providers.environmentVariable("PGP_SIGNING_KEY")
val signArtifacts: Boolean = !pgpSigningKey.orNull.isNullOrEmpty()

tasks.withType<Sign>().configureEach { isEnabled = signArtifacts }

signing {
    useInMemoryPgpKeys(
        project.providers.environmentVariable("PGP_SIGNING_KEY").orNull,
        project.providers.environmentVariable("PGP_SIGNING_KEY_PASSPHRASE").orNull
    )
    publishing.publications.configureEach {
        if (signArtifacts) {
            signing.sign(this)
        }
    }
}

