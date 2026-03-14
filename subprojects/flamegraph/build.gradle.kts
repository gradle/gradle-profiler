import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask
import org.gradle.process.ExecOperations

plugins {
    id("java-library")
    id("java-test-fixtures")
    id("com.github.node-gradle.node")
}

node {
    download = true
    version = "24.14.0"
    npmInstallCommand = "ci"
    workDir = layout.buildDirectory.dir("node")
}

abstract class NpmCmdlineTask : NpmTask() {

    init {
        args.set(cmd.map<List<String>> {
            it.split("\\s+".toRegex()).drop(1)
        })
        npmCommand.set(cmd.map {
            listOf(it.split("\\s+".toRegex()).first())
        })
    }

    @get:Input
    @get:Option(option = "cmd", description = "The npm script to run")
    abstract val cmd: Property<String>

}

tasks.register<NpmCmdlineTask>("npm")

abstract class NpxCmdlineTask : NpxTask() {

    init {
        args.set(cmd.map<List<String>> {
            it.split("\\s+".toRegex()).drop(1)
        })
        command.set(cmd.map {
            it.split("\\s+".toRegex()).first()
        })
    }

    @get:Input
    @get:Option(option = "cmd", description = "The npm script to run")
    abstract val cmd: Property<String>

}

tasks.register<NpxCmdlineTask>("npx")

tasks.register<NpxTask>("serve") {
    command.set("npx")
    args.set(listOf("vite"))
}

val buildVite = tasks.register<NpxTask>("buildVite") {
    dependsOn(tasks.npmInstall)
    command.set("vite")
    args.set(listOf("build"))
}

tasks.build {
    dependsOn(buildVite)
}

tasks.clean {
    delete(layout.projectDirectory.dir("node_modules"))
}

// Embed the vite-built index.html into the jar as a classpath resource so that
// FlamegraphGenerator can load it via Class.getResourceAsStream().
tasks.named<ProcessResources>("processResources") {
    dependsOn(buildVite)
    from(layout.buildDirectory.file("vite/index.html")) {
        into("org/gradle/profiler/flamegraph")
    }
}

abstract class GenerateDemoTask : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:InputFiles
    @get:Option(option = "stacks", description = "The stacks files to embed in the demo")
    abstract val stacks: ListProperty<String>

    @TaskAction
    fun generate() {
        execOperations.javaexec {
            classpath(this@GenerateDemoTask.classpath)
            mainClass.set("org.gradle.profiler.flamegraph.FlamegraphGenerator")
            args(stacks.get() + listOf(outputFile.get().asFile.absolutePath))
        }
    }

}

tasks.register<GenerateDemoTask>("buildDemo") {
    classpath.from(sourceSets.main.get().runtimeClasspath)
    outputFile = layout.buildDirectory.file("demo/index.html")
}
