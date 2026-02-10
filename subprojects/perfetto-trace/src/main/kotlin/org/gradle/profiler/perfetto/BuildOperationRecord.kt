package org.gradle.profiler.perfetto

import java.util.stream.Stream

typealias BuildOperationId = Long

sealed interface BuildOperationRecord {
    val id: Long
}

class BuildOperationStart @JvmOverloads constructor(
    override val id: Long,
    val displayName: String,
    val startTime: Long,
    details: Map<String, *>? = null,
    val detailsClassName: String? = null,
    val parentId: Long? = null
) : BuildOperationRecord {
    val details: Map<String, Any?>? = details?.toMap()

    override fun toString(): String {
        return "BuildOperationStart{$id->$displayName}"
    }
}

class BuildOperationFinish @JvmOverloads constructor(
    override val id: Long,
    val endTime: Long,
    result: Map<String, *>? = null,
    val resultClassName: String? = null,
    val failure: String? = null,
) : BuildOperationRecord {
    val result: Map<String, Any?>? = result?.toMap()

    override fun toString(): String {
        return "BuildOperationFinish{$id}"
    }
}

class BuildOperationProgress(
    override val id: Long,
    val time: Long,
    details: Map<String, Any?>?,
    val detailsClassName: String?
) : BuildOperationRecord {
    val details: Map<String, Any?>? = details?.toMap()

    override fun toString(): String {
        return "Progress{details=$details, detailsClassName='$detailsClassName'}"
    }
}

fun interface BuildOperationFinishVisitor {
    fun visit(start: BuildOperationStart, finish: BuildOperationFinish)
}

interface BuildOperationVisitor {

    /**
     * Visits the current build operation record.
     *
     * Returns a post-visit callback that will be called after all children have been visited.
     */
    fun visit(start: BuildOperationStart): BuildOperationFinishVisitor

    fun visit(progress: BuildOperationProgress) {}

    companion object {

        @JvmStatic
        fun visitLogs(operationLog: Stream<BuildOperationRecord>, visitor: BuildOperationVisitor) {
            val openBuildOperations = mutableMapOf<Long, Pair<BuildOperationStart, BuildOperationFinishVisitor>>()
            var count = 0
            fun helper(record: BuildOperationRecord) {
                when (record) {
                    is BuildOperationStart -> {
                        openBuildOperations[record.id] = record to visitor.visit(record)
                    }

                    is BuildOperationFinish -> {
                        val openBuildOp = openBuildOperations.remove(record.id)
                        openBuildOp?.let { (start, postVisit) ->
                            postVisit.visit(start, record)
                        }
                    }

                    is BuildOperationProgress -> visitor.visit(record)
                }
            }

            for (record in operationLog) {
                count++
                helper(record)
            }
        }
    }
}
