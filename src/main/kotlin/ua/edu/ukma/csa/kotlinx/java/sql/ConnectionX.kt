package ua.edu.ukma.csa.kotlinx.java.sql

import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.SQLException

inline fun <T> Connection.transaction(transactionHandler: Connection.() -> T): T {
    val prevCommitState = autoCommit
    autoCommit = false
    return try {
        val res = transactionHandler()
        commit()
        autoCommit = prevCommitState
        res
    } catch (e: SQLException) {
        rollback()
        autoCommit = prevCommitState
        throw e
    }
}

fun Connection.execute(@Language("SQL") sql: String) = createStatement().use { it.execute(sql) }
fun Connection.executeUpdate(@Language("SQL") sql: String) = createStatement().use { it.executeUpdate(sql) }
