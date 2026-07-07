import tasks.FetchPerfettoProtoTask

plugins {
    id("profiler.java-library")
    id("profiler.publication")
    alias(libs.plugins.protobuf)
}

description = "Generated Perfetto protobuf classes"

repositories {
    mavenCentral()
}

// The Perfetto trace proto is fetched from a pinned upstream release and verified by checksum,
// instead of vendoring ~16k lines of generated proto into the repository.
// To update Perfetto, bump the version and the checksum together (sha256 of the raw upstream file).
val fetchPerfettoProto = tasks.register<FetchPerfettoProtoTask>("fetchPerfettoProto") {
    version = "v55.3"
    sha256 = "e06bad0f59c13fd68557f5e323a344d5ca1e96fa8897882e761450c734a60ad2"
    outputDir.convention(layout.buildDirectory.dir("perfetto-proto"))
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
}

// Pass the task (not just the dir) so every consumer of the proto source — generateProto,
// processResources, sourcesJar — inherits the dependency on the download.
sourceSets {
    main {
        proto {
            srcDir(fetchPerfettoProto)
        }
    }
}

dependencies {
    api(libs.protobuf.java)
}
