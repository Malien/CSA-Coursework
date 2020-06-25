package ua.edu.ukma.csa.kotlinx.java.sql

import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

inline fun <T> Connection.transaction(transactionHandler: (connection: Connection) -> T): T {
    val prevCommitState = autoCommit
    autoCommit = false
    return try {
        val res = transactionHandler(this)
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
fun Connection.executeQuery(@Language("SQL") sql: String): ResultSet =
    createStatement().use { it.executeQuery(sql) }
