import java.net.URI

plugins {
    java
    groovy
    application
    `maven-publish`
}

allprojects {
    group = "org.gradle.profiler"
    version = property("profiler.version") as String
}

repositories {
    jcenter()
    maven {
        url = uri("https://repo.gradle.org/gradle/repo")
    }
}

val profilerPlugins by configurations.creating

dependencies {
    implementation("org.gradle:gradle-tooling-api:5.2.1")
    implementation("com.google.code.findbugs:annotations:3.0.1")
    implementation("com.google.guava:guava:27.1-android") {
        because("Gradle uses the android variant as well and we are running the same code there.")
    }
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")
    implementation("com.typesafe:config:1.3.3")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("com.github.javaparser:javaparser-core:3.1.3")
    implementation("org.apache.ant:ant-compress:1.5")
    implementation("commons-io:commons-io:2.6")
    implementation("org.gradle.org.openjdk.jmc:flightrecorder:7.0.0-alpha01")
    implementation("com.android.tools.build:builder-model:3.0.0")

    profilerPlugins(project(":chrome-trace")) {
        isTransitive = false
    }
    profilerPlugins(project(":build-operations")) {
        isTransitive = false
    }

    runtimeOnly("org.slf4j:slf4j-simple:1.7.10")
    testCompile("org.codehaus.groovy:groovy:2.4.7")
    testCompile("org.spockframework:spock-core:1.1-groovy-2.4")
    testRuntime("cglib:cglib:3.2.6")
    testRuntime("org.objenesis:objenesis:2.6")
}

allprojects {
    pluginManager.withPlugin("java") {
        java {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }
}
application.mainClassName = "org.gradle.profiler.Main"

tasks.processResources {
    into("META-INF/jars") {
        from(profilerPlugins) {
            // Removing the version from the JARs here, since they are referenced by name in production code.
            rename("""(.*)-\d\.\d.*\.jar""", "${'$'}1.jar")
        }
    }
}

val profilerDistribution = artifacts.add("archives", tasks.distZip.flatMap { it.archiveFile }) {
    type = "zip"
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(profilerDistribution)
        }
    }
}

publishing {
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
    val isSnapshot = version.toString().endsWith("-SNAPSHOT")
    val repositoryQualifier = if (isSnapshot) "snapshots" else "releases"
    return uri("https://repo.gradle.org/gradle/ext-$repositoryQualifier-local")
}

buildScan {
    isCaptureTaskInputFiles = true
}
