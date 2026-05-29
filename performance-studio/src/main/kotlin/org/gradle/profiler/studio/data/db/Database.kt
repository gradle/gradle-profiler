package org.gradle.profiler.studio.data.db

import org.gradle.profiler.studio.data.AppPaths
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object StudioDatabase {
    fun connect(): Database {
        val file = AppPaths.databaseFile
        val db = Database.connect(
            url = "jdbc:sqlite:${file.absolutePath}",
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            SchemaUtils.create(Projects, Runs)
        }
        return db
    }
}
