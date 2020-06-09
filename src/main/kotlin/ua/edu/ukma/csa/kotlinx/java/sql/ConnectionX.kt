package ua.edu.ukma.csa.kotlinx.java.sql

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.SQLException

inline fun <T> Connection.transaction(transactionHandler: Connection.() -> T): Either<SQLException, T> {
    val prevCommitState = autoCommit
    autoCommit = false
    return try {
        val res = transactionHandler()
        commit()
        autoCommit = prevCommitState
        Right(res)
    } catch (e: SQLException) {
        rollback()
        autoCommit = prevCommitState
        Left(e)
    }
}

fun Connection.execute(@Language("SQL") sql: String) = createStatement().use { it.execute(sql) }
fun Connection.executeUpdate(@Language("SQL") sql: String) = createStatement().use { it.executeUpdate(sql) }
