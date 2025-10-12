import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask

plugins {
    id("com.github.node-gradle.node")
}

node {
    download = true
    version = "24.1.0"
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
    command.set("vite")
    args.set(listOf("build"))
}

tasks.register("build") {
    dependsOn(buildVite)
}
