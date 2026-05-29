package org.gradle.profiler.studio.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gradle.profiler.studio.data.db.Projects
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class ProjectRepository(private val db: Database) {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    init {
        refresh()
    }

    fun add(name: String, path: String): Project {
        val now = Instant.now()
        val id = transaction(db) {
            Projects.insertAndGetId {
                it[Projects.name] = name
                it[Projects.path] = path
                it[createdAt] = now
            }.value
        }
        refresh()
        return Project(id, name, path, now)
    }

    fun remove(projectId: Int) {
        transaction(db) {
            Projects.deleteWhere { Projects.id eq projectId }
        }
        refresh()
    }

    private fun refresh() {
        _projects.value = transaction(db) {
            Projects.selectAll()
                .orderBy(Projects.name to SortOrder.ASC)
                .map { it.toProject() }
        }
    }

    private fun ResultRow.toProject() = Project(
        id = this[Projects.id].value,
        name = this[Projects.name],
        path = this[Projects.path],
        createdAt = this[Projects.createdAt],
    )
}
