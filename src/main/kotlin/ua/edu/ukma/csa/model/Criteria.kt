package ua.edu.ukma.csa.model

import kotlinx.serialization.Serializable
import ua.edu.ukma.csa.kotlinx.transformNotNull
import java.sql.PreparedStatement

@Serializable
data class Criteria(
    val name: String? = null,
    val fromPrice: Double? = null,
    val toPrice: Double? = null,
    val inGroups: Set<GroupID>? = null
) {
    fun preparedNameSQL(columnName: String) = name.transformNotNull { "$columnName LIKE ?" }

    fun preparedPriceSQL(columnName: String) =
        if (fromPrice != null) {
            if (toPrice != null) "$columnName BETWEEN ? AND ?"
            else "$columnName >= ?"
        } else {
            toPrice.transformNotNull { "$columnName <= ?" }
        }

    fun preparedGroupSQL(columnName: String) =
        inGroups.transformNotNull { "$columnName IN (${it.joinToString { "?" }})" }

    fun insertNamePlaceholders(statement: PreparedStatement, idx: Int) =
        idx + if (name != null) {
            statement.setString(idx, "%$name%")
            1
        } else 0

    fun insertPricePlaceholders(statement: PreparedStatement, idx: Int) =
        idx + if (fromPrice != null) {
            if (toPrice != null) {
                statement.setDouble(idx, fromPrice)
                statement.setDouble(idx + 1, toPrice)
                2
            } else {
                statement.setDouble(idx, fromPrice)
                1
            }
        } else {
            if (toPrice != null) {
                statement.setDouble(idx, toPrice)
                1
            } else 0
        }

    fun insertGroupsPlaceholders(statement: PreparedStatement, idx: Int) =
        inGroups?.fold(idx) { accumulator, (id) ->
            statement.setInt(accumulator, id)
            accumulator + 1
        } ?: idx
}

