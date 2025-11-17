import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.GZIPOutputStream

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

abstract class GenerateDemoTask : DefaultTask() {

    @get:InputFiles
    abstract val applicationBundle: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:InputFiles
    @get:Option(option = "stacks", description = "The stacks files to embed in the demo")
    abstract val stacks: ListProperty<String>

    @TaskAction
    fun generate() {
        val stacksFiles = stacks.get().map { File(it) }
        stacksFiles.forEach {
            require(it.isAbsolute) { "Stacks file '$it' must be absolute" }
            require(it.isFile) { "Stacks file $it does not exist" }
        }
        require(stacksFiles.map { it.name }.distinct().size == stacksFiles.size) {
            "Stacks files must have distinct names"
        }

        var foundTargetLine = false
        outputFile.get().asFile.outputStream().buffered().use { output ->
            applicationBundle.get().asFile.inputStream().buffered().use { input ->
                while (!foundTargetLine) {
                    val byteLine = input.readByteLine() ?: break
                    val line = String(byteLine, 0, byteLine.size, StandardCharsets.UTF_8)
                    if (line.trim().startsWith("window.__ENCODED_EMBEDDED_STACKS__")) {
                        foundTargetLine = true
                        output.writeUtf8("window.__ENCODED_EMBEDDED_STACKS__ = {\n")
                        stacksFiles.forEachIndexed { index, stackFile ->
                            output.writeUtf8("\"${stackFile.name}\":\"")
                            stackFile.inputStream().buffered().use { stackStream ->
                                val gzip = GZIPOutputStream(Base64.getEncoder().wrap(output))
                                stackStream.copyTo(gzip)
                                gzip.finish()
                            }
                            output.writeUtf8("\"")
                            if (index < stacksFiles.size - 1) {
                                output.writeUtf8(",")
                            }
                            output.writeUtf8("\n")
                        }
                        output.writeUtf8("};\n")
                    } else {
                        output.write(byteLine)
                    }
                }
                if (!foundTargetLine) {
                    throw GradleException("Could not find target line in application bundle to embed stacks")
                }
                input.copyTo(output)
            }
        }
    }

    fun InputStream.readByteLine(): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var byte = this.read()
        if (byte == -1) return null
        while (byte != -1) {
            buffer.write(byte)
            if (byte == '\n'.code) break
            byte = this.read()
        }
        return buffer.toByteArray()
    }

    fun OutputStream.writeUtf8(line: String) {
        this.write(line.toByteArray(StandardCharsets.UTF_8))
    }

}

tasks.register<GenerateDemoTask>("buildDemo") {
    dependsOn(buildVite)
    applicationBundle = layout.buildDirectory.file("vite/index.html")
    outputFile = layout.buildDirectory.file("demo/index.html")
}
