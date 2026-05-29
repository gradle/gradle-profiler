package org.gradle.profiler.studio.data.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Projects : IntIdTable("project") {
    val name = varchar("name", 255)
    val path = varchar("path", 1024)
    val createdAt = timestamp("created_at")
}

object Runs : IntIdTable("run") {
    val projectId = reference("project_id", Projects)
    val outputName = varchar("output_name", 255)
    val outputDir = varchar("output_dir", 1024)
    val status = varchar("status", 32)
    val startedAt = timestamp("started_at")
    val endedAt = timestamp("ended_at").nullable()
    val exitCode = integer("exit_code").nullable()
    val configJson = text("config_json").nullable()
}
