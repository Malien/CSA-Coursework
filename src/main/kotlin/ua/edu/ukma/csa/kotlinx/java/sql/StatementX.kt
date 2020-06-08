package ua.edu.ukma.csa.kotlinx.java.sql

import java.sql.Statement

fun Statement.transaction(transactionHandler: Statement.() -> Unit) {
}