import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

plugins {
    id("java-library")
    id("java-test-fixtures")
    id("com.github.node-gradle.node")
}

fun getTarget(isWasmPack: Boolean = false): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase().let { if ("arm64" in it || "aarch64" in it) "aarch64" else "x86_64" }
    return when {
        "mac" in os || "darwin" in os -> "$arch-apple-darwin"
        "win" in os -> "$arch-pc-windows-msvc"
        "linux" in os -> if (isWasmPack) "$arch-unknown-linux-musl" else "$arch-unknown-linux-gnu"
        else -> error("Unsupported OS: $os")
    }
}

object Extensions {

    val archive = if (System.getProperty("os.name").lowercase().contains("windows")) ".zip" else ".tar.gz"

    val executable = if (System.getProperty("os.name").lowercase().contains("windows")) ".exe" else ""

}

@DisableCachingByDefault(because = "Not worth caching")
abstract class DownloadTask : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val file = destination.get().asFile.also { it.parentFile.mkdirs() }
        val request = HttpRequest.newBuilder().uri(URI.create(url.get())).GET().build()
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        client.send(request, HttpResponse.BodyHandlers.ofFile(file.toPath()))
    }

}

val downloadRustup = tasks.register<DownloadTask>("downloadRustup") {
    val ext = Extensions.executable
    url.set("https://static.rust-lang.org/rustup/dist/${getTarget()}/rustup-init$ext")
    destination.set(layout.buildDirectory.file("rust-downloads/rustup-init$ext"))

    doLast {
        destination.get().asFile.setExecutable(true)
    }
}

abstract class InstallRustTask : DefaultTask() {

    @get:Input
    abstract val rustVersion: Property<String>

    @get:InputFile
    abstract val rustupInitFile: RegularFileProperty

    @get:OutputDirectory
    abstract val cargoHome: DirectoryProperty

    @get:OutputDirectory
    abstract val rustupHome: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun install() {
        execOperations.exec {
            environment(mapOf(
                "RUSTUP_HOME" to rustupHome.get().asFile.absolutePath,
                "CARGO_HOME" to cargoHome.get().asFile.absolutePath,
            ))
            executable = rustupInitFile.get().asFile.absolutePath
            args(
                "-y",
                "--no-modify-path",
                "--profile", "minimal",
                "--default-toolchain", rustVersion.get(),
                "--target", "wasm32-unknown-unknown"
            )
        }
    }

}

val installRust = tasks.register<InstallRustTask>("installRust") {
    rustVersion.set("1.94.0")
    rustupInitFile.set(downloadRustup.flatMap { it.destination })
    cargoHome.set(layout.buildDirectory.dir("cargo-home"))
    rustupHome.set(layout.buildDirectory.dir("rustup-home"))
}

val downloadWasmPack = tasks.register<DownloadTask>("downloadWasmPack") {

    val wasmPackVersion = "0.14.0"
    val ext = Extensions.archive
    url.set("https://github.com/drager/wasm-pack/releases/download/v$wasmPackVersion/wasm-pack-v$wasmPackVersion-${getTarget(isWasmPack = true)}$ext")
    destination.set(layout.buildDirectory.file("rust-downloads/wasm-pack$ext"))
}

abstract class UnpackWasmPackTask : DefaultTask() {

    @get:InputFile
    abstract val archive: RegularFileProperty

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val filesystemOperations: FileSystemOperations

    @get:OutputFile
    abstract val binFile: RegularFileProperty

    @TaskAction
    fun unpack() {
        filesystemOperations.copy {
            from(archive.map { f -> if (f.asFile.name.endsWith(".zip")) archiveOperations.zipTree(f) else archiveOperations.tarTree(f) }) {
                eachFile {
                    this.relativePath = RelativePath(true, *this.relativePath.segments.drop<String>(1).toTypedArray<String>())
                    if (name == "wasm-pack${Extensions.executable}") {
                        permissions {
                            user {
                                execute = true
                            }
                        }
                    }
                }
                this.includeEmptyDirs = false
            }
            into(outputDir)
        }
    }

}

val unpackWasmPack = tasks.register<UnpackWasmPackTask>("unpackWasmPack") {
    archive.set(downloadWasmPack.flatMap { it.destination })
    outputDir.set(layout.buildDirectory.dir("wasm-pack"))
    binFile = outputDir.map { it.file("wasm-pack${Extensions.executable}") }
}

abstract class CompileRustTask : DefaultTask() {

    @get:InputDirectory
    abstract val srcDir: DirectoryProperty

    @get:InputFile
    abstract val wasmPackBin: RegularFileProperty

    @get:InputDirectory
    abstract val cargoHome: DirectoryProperty

    @get:InputDirectory
    abstract val rustupHome: DirectoryProperty

    @get:Internal
    abstract val buildDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun compile() {
        execOperations.exec {
            environment(mapOf(
                "RUSTUP_HOME" to rustupHome.get().asFile.absolutePath,
                "CARGO_HOME" to cargoHome.get().asFile.absolutePath,
                "PATH" to "${cargoHome.get().asFile.absolutePath}/bin${File.pathSeparator}${System.getenv("PATH")}",
                "CARGO_TARGET_DIR" to buildDir.get().asFile.absolutePath,
            ))
            workingDir(srcDir.get().asFile)
            executable = wasmPackBin.get().asFile.absolutePath
            args(
                "build",
                "--locked",
                "--target", "web",
                "--out-dir", outputDir.get().asFile.absolutePath,
            )
        }
    }

}

val rustSrcDir = layout.projectDirectory.dir("src/main/rust")

val compileRust = tasks.register<CompileRustTask>("compileRust") {
    srcDir.set(rustSrcDir)
    wasmPackBin.set(unpackWasmPack.flatMap { it.binFile })
    cargoHome.set(installRust.flatMap { it.cargoHome })
    rustupHome.set(installRust.flatMap { it.rustupHome })
    buildDir.set(layout.buildDirectory.dir("wasm-build"))
    outputDir.set(layout.buildDirectory.dir("wasm"))
}

abstract class CargoCmdlineTask : DefaultTask() {

    @get:Input
    @get:Option(option = "cmd", description = "The cargo command to run")
    abstract val cmd: Property<String>

    @get:InputDirectory
    abstract val cargoHome: DirectoryProperty

    @get:InputDirectory
    abstract val rustupHome: DirectoryProperty

    @get:InputDirectory
    abstract val workingDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun run() {
        execOperations.exec {
            environment(mapOf(
                "RUSTUP_HOME" to rustupHome.get().asFile.absolutePath,
                "CARGO_HOME" to cargoHome.get().asFile.absolutePath,
                "PATH" to "${cargoHome.get().asFile.absolutePath}/bin${File.pathSeparator}${System.getenv("PATH")}"
            ))
            workingDir(this@CargoCmdlineTask.workingDir.get().asFile)
            executable = "${cargoHome.get().asFile.absolutePath}/bin/cargo${Extensions.executable}"
            args(cmd.get().split("\\s+".toRegex()))
        }
    }
}

tasks.register<CargoCmdlineTask>("cargo") {
    cargoHome.set(installRust.flatMap { it.cargoHome })
    rustupHome.set(installRust.flatMap { it.rustupHome })
    workingDir.set(rustSrcDir)
}

val cargoTest = tasks.register<CargoCmdlineTask>("cargoTest") {
    cargoHome.set(installRust.flatMap { it.cargoHome })
    rustupHome.set(installRust.flatMap { it.rustupHome })
    workingDir.set(rustSrcDir)
    cmd.set("test")
}

tasks.check {
    dependsOn(cargoTest)
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
    dependsOn(compileRust)
    args.set(listOf("vite"))
}

val buildVite = tasks.register<NpxTask>("buildVite") {
    dependsOn(tasks.npmInstall)
    dependsOn(compileRust)
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

@DisableCachingByDefault(because = "Large output file size")
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
