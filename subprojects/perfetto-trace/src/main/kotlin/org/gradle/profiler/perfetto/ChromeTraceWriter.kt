package org.gradle.profiler.perfetto

import com.google.protobuf.CodedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import perfetto.protos.BuiltinClockOuterClass.BuiltinClock
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot.Clock
import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotation
import perfetto.protos.ProcessDescriptorOuterClass.ProcessDescriptor
import perfetto.protos.ThreadDescriptorOuterClass.ThreadDescriptor
import perfetto.protos.TracePacketOuterClass.TracePacket
import perfetto.protos.TrackDescriptorOuterClass.TrackDescriptor
import perfetto.protos.TrackEventOuterClass.TrackEvent

/**
 * Chrome-trace process ID.
 *
 * Structurally, a group of threads that is independent of others
 */
typealias CtProcessId = Int

/**
 * Chrome-trace thread ID.
 *
 * Structurally, a thread lane within a [process][CtProcessId].
 */
typealias CtThreadId = Int

/**
 * Chrome-trace track ID.
 *
 * Structurally, a thread lane within a [process][CtProcessId].
 */
typealias CtTrackUuid = Long

internal class ChromeTraceWriter(val outputFile: File) : BuildOperationVisitor, AutoCloseable {

    data class Summary(
        val packetCount: Int,
        val buildScanUrl: String?
    )

    private var packetCount = 0
    private val uuidCounter = AtomicLong(1)
    private val knownPidTid = mutableMapOf<CtProcessId, MutableMap<CtThreadId, CtTrackUuid>>()
    private val startTime = AtomicLong(0)
    private val fileOutputStream: OutputStream = Files.newOutputStream(outputFile.toPath())
    private val codedStream = CodedOutputStream.newInstance(fileOutputStream)

    private val pidTidToBuildOp = mutableMapOf<CtProcessId, MutableMap<CtThreadId, BuildOperationId?>>()

    private var expectBuildScanLinkProgressEvent = false
    private var buildScanUrl: String? = null

    fun getSummary(): Summary {
        return Summary(packetCount, buildScanUrl)
    }

    override fun close() {
        codedStream.flush()
        fileOutputStream.close()
    }

    private fun writeTracePacket(packet: TracePacket) {
        codedStream.writeMessage(1, packet)
        packetCount++
    }

    override fun visit(start: BuildOperationStart): BuildOperationFinishVisitor {
        if (packetCount == 0) {
            onFirstRecord(start)
        }

        val ctProcessId = 0
        if (!knownPidTid.containsKey(ctProcessId)) {
            writeTracePacket(
                TracePacket.newBuilder()
                    .setTrustedPacketSequenceId(1)
                    .setTrackDescriptor(
                        TrackDescriptor.newBuilder()
                            .setUuid(0) // irrelevant, but needed
                            .setProcess(
                                ProcessDescriptor.newBuilder()
                                    .setPid(ctProcessId)
                                    .setProcessName("Gradle Build Operation Trace")
                            )
                    )
                    .build()
            )
            knownPidTid[ctProcessId] = mutableMapOf()
            pidTidToBuildOp[ctProcessId] = mutableMapOf()
        }

        val uuid: Long
        val knownTid = knownPidTid[ctProcessId]!!
        val tidToBuildOp = pidTidToBuildOp[ctProcessId]!!
        val ctThreadId = determineThreadId(start.parentId, tidToBuildOp)
        if (!(knownTid.containsKey(ctThreadId))) {
            uuid = uuidCounter.getAndIncrement()
            writeTracePacket(
                TracePacket.newBuilder()
                    .setTrustedPacketSequenceId(1)
                    .setTrackDescriptor(
                        TrackDescriptor.newBuilder()
                            .setUuid(uuid)
                            .setThread(
                                ThreadDescriptor.newBuilder()
                                    .setPid(ctProcessId)
                                    .setTid(ctThreadId)
                                    .setThreadName("abstract thread")
                            )
                    )
                    .build()
            )
            knownTid[ctThreadId] = uuid
        } else {
            uuid = knownTid[ctThreadId]!!
        }

        writeTracePacket(
            TracePacket.newBuilder()
                .setTimestampClockId(64)
                .setTimestamp(start.startTime - startTime.get())
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(
                    TrackEvent.newBuilder()
                        .setTrackUuid(uuid)
                        .setName(start.displayName)
                        .addCategories(start.detailsClassName ?: "")
                        .addDebugAnnotations(toDebugAnnotations(start.details, "details"))
                        .setType(TrackEvent.Type.TYPE_SLICE_BEGIN)
                )
                .build()
        )

        val oldParent = tidToBuildOp[ctThreadId]
        tidToBuildOp[ctThreadId] = start.id

        return BuildOperationFinishVisitor { _, finish ->
            tidToBuildOp[ctThreadId] = oldParent
            writeTracePacket(
                TracePacket.newBuilder()
                    .setTimestampClockId(64)
                    .setTimestamp(finish.endTime - startTime.get())
                    .setTrustedPacketSequenceId(1)
                    .setTrackEvent(
                        TrackEvent.newBuilder()
                            .setTrackUuid(uuid)
                            .addDebugAnnotations(
                                toDebugAnnotations(
                                    mapOf(
                                        "id" to start.id,
                                        "parentId" to start.parentId
                                    ), "operation"
                                )
                            )
                            .addDebugAnnotations(toDebugAnnotations(finish.result, "result"))
                            .setType(TrackEvent.Type.TYPE_SLICE_END)
                    )
                    .build()
            )
        }
    }

    override fun visit(progress: BuildOperationProgress) {
        // A heuristic to extract the build scan link from the progress events
        // that represent console output at the end of the build
        if (progress.detailsClassName == "org.gradle.internal.logging.events.StyledTextOutputEvent") {
            val details =
                progress.details?.takeIf { it["category"] == "com.gradle.develocity.agent.gradle.DevelocityPlugin" }
                    ?: return

            @Suppress("UNCHECKED_CAST")
            val spans: Map<String, String> = details["spans"]?.let { it as? List<Map<String, String>> }?.get(0)
                ?: return

            val text = spans["text"] ?: return
            if (text.startsWith("Publishing build scan...") || text.startsWith("Publishing Build Scan...") || text.startsWith(
                    "Publishing Build Scan to Develocity..."
                )
            ) {
                expectBuildScanLinkProgressEvent = true
            } else if (expectBuildScanLinkProgressEvent) {
                expectBuildScanLinkProgressEvent = false
                if (text.startsWith("http")) {
                    onBuildScanUrl(text)
                }
            }
        }
    }

    private fun onBuildScanUrl(buildScanUrl: String) {
        this.buildScanUrl = buildScanUrl

        val extraTrackId = uuidCounter.getAndIncrement()
        writeTracePacket(
            TracePacket.newBuilder()
                .setTimestampClockId(64)
                .setTimestamp(0)
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(
                    TrackEvent.newBuilder()
                        .setTrackUuid(extraTrackId)
                        .setName("Build scan: $buildScanUrl")
                        .setType(TrackEvent.Type.TYPE_INSTANT)
                )
                .build()
        )
    }

    private fun determineThreadId(
        parentId: BuildOperationId?,
        tidToBuildOp: Map<CtThreadId, BuildOperationId?>
    ): Int {
        val compatibleThread = tidToBuildOp.entries
            .find { it.value == parentId }
            ?.key
        if (compatibleThread != null) {
            return compatibleThread
        }
        val emptyThread = tidToBuildOp.entries
            .find { it.value == null }
            ?.key
        // We start counting at 1, since thread id 0 is reserved
        return emptyThread ?: (tidToBuildOp.size + 1)
    }

    private fun onFirstRecord(record: BuildOperationStart) {
        startTime.set(record.startTime)

        writeTracePacket(
            TracePacket.newBuilder()
                .setTrustedPacketSequenceId(1)
                .setClockSnapshot(
                    ClockSnapshot.newBuilder()
                        // set our custom clock
                        // - let the 0 of our clock be the startTime on the global boot-time clock
                        // - use 'ms' unit since that's anyway the precision we get by the build operation infrastructure
                        .addClocks(
                            Clock.newBuilder()
                                .setTimestamp(0)
                                .setUnitMultiplierNs(1000 * 1000) // unit is 'ms'
                                .setClockId(64) // first user-defined available clock ID
                        )
                        .addClocks(
                            Clock.newBuilder()
                                .setTimestamp(startTime.get())
                                .setClockId(BuiltinClock.BUILTIN_CLOCK_BOOTTIME_VALUE)
                        )
                )
                .build()
        )
    }

    private fun toDebugAnnotations(args: Map<String, Any?>?, name: String): DebugAnnotation {
        return DebugAnnotation.newBuilder()
            .setName(name)
            .addAllDictEntries(args?.entries?.map { e ->
                @Suppress("UNCHECKED_CAST")
                return@map if (e.value is Map<*, *>) toDebugAnnotations(e.value as Map<String, Any>, e.key)
                else DebugAnnotation.newBuilder().setName(e.key).setStringValue(e.value.toString()).build()
            }.orEmpty())
            .build()
    }
}
