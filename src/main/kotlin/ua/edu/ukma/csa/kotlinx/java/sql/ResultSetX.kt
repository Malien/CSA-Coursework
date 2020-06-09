package ua.edu.ukma.csa.kotlinx.java.sql

import java.sql.ResultSet

operator fun ResultSet.iterator() = iterator {
    while (next()) yield(this@iterator)
}
