package org.gradle.profiler.studio.data

import kotlinx.serialization.json.Json
import org.gradle.profiler.studio.data.db.Runs
import org.gradle.profiler.studio.domain.ConfigDraft
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class RunRepository(private val db: Database) {

    private val json = Json { ignoreUnknownKeys = true }

    fun nextOutputName(projectId: Int): String {
        val nums = transaction(db) {
            Runs.selectAll()
                .where { Runs.projectId eq projectId }
                .mapNotNull { it[Runs.outputName].removePrefix("profiler-out-").toIntOrNull() }
        }
        val next = (nums.maxOrNull() ?: 0) + 1
        return "profiler-out-$next"
    }

    fun create(projectId: Int, outputName: String, outputDir: String, config: ConfigDraft): Run {
        val now = Instant.now()
        val serialized = json.encodeToString(ConfigDraft.serializer(), config)
        val id = transaction(db) {
            Runs.insertAndGetId {
                it[Runs.projectId] = projectId
                it[Runs.outputName] = outputName
                it[Runs.outputDir] = outputDir
                it[Runs.status] = RunStatus.Running.name
                it[Runs.startedAt] = now
                it[Runs.configJson] = serialized
            }.value
        }
        return Run(id, projectId, outputName, outputDir, RunStatus.Running, now, null, null, config)
    }

    fun finish(runId: Int, status: RunStatus, exitCode: Int?) {
        transaction(db) {
            Runs.update({ Runs.id eq runId }) {
                it[Runs.status] = status.name
                it[Runs.endedAt] = Instant.now()
                it[Runs.exitCode] = exitCode
            }
        }
    }

    fun listForProject(projectId: Int): List<Run> = transaction(db) {
        Runs.selectAll()
            .where { Runs.projectId eq projectId }
            .orderBy(Runs.startedAt to SortOrder.DESC)
            .map { it.toRun() }
    }

    fun findById(runId: Int): Run? = transaction(db) {
        Runs.selectAll().where { Runs.id eq runId }.firstOrNull()?.toRun()
    }

    fun deleteForProject(projectId: Int) {
        transaction(db) {
            Runs.deleteWhere { Runs.projectId eq projectId }
        }
    }

    private fun ResultRow.toRun() = Run(
        id = this[Runs.id].value,
        projectId = this[Runs.projectId].value,
        outputName = this[Runs.outputName],
        outputDir = this[Runs.outputDir],
        status = RunStatus.valueOf(this[Runs.status]),
        startedAt = this[Runs.startedAt],
        endedAt = this[Runs.endedAt],
        exitCode = this[Runs.exitCode],
        config = this[Runs.configJson]?.let {
            runCatching { json.decodeFromString(ConfigDraft.serializer(), it) }.getOrNull()
        },
    )
}
