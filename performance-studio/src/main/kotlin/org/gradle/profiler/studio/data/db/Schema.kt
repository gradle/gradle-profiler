package org.gradle.profiler.studio.data.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Projects : IntIdTable("project") {
    val name = varchar("name", 255)
    val path = varchar("path", 1024)
    val createdAt = timestamp("created_at")
}
