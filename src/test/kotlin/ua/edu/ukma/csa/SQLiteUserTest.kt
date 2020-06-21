package ua.edu.ukma.csa

import arrow.core.flatMap
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.model.UserID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteUserTest {

    private val model = SQLiteModel(":memory:")

    @AfterAll
    fun closeDB() {
        model.close()
    }

    @BeforeEach
    fun cleanup() {
        model.clear()
    }

    @Test
    fun `add user checks`() {
        val validUser = model.addUser("login", "Str0nkPassw")
        assertRight("login", validUser.map { it.login })
        val sameUser = model.addUser("login", "An0therPassw")
        assertLeftType<ModelException.UserLoginAlreadyExists>(sameUser)
        val weakPassword = model.addUser("another_login", "weak")
        assertLeftType<ModelException.Password>(weakPassword)
    }

    @Test
    fun `retrieve user`() {
        val validUser = model.addUser("login", "Str0nkPassw")
        assertRight("login", validUser.map { it.login })
        val notFound = model.getUser(UserID.UNSET)
        assertLeftType<ModelException.UserDoesNotExist>(notFound)
        val notFoundByLogin = model.getUser("does_not_exist")
        assertLeftType<ModelException.UserDoesNotExist>(notFoundByLogin)
        val retrievedByID = validUser.flatMap {
            model.getUser(it.id)
        }
        assertEquals(validUser, retrievedByID)
        val retrievedByLogin = model.getUser("login")
        assertEquals(validUser, retrievedByLogin)
    }
}