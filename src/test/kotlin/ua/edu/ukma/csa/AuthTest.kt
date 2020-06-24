package ua.edu.ukma.csa

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.model.User
import java.net.InetAddress
import java.net.InetSocketAddress

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthTest {

    private val model = SQLiteModel(":memory:")
    private val server = HttpServer.create()
    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(JsonFeature) {
            serializer = KotlinxSerializer(Configuration.json)
        }
    }
    private lateinit var user: User

    companion object {
        private const val PORT = 3330
        private const val HOSTNAME = "localhost"
        private const val API_URL = "http://$HOSTNAME:$PORT"
        private const val SECRET = "secret"
    }

    init {
        server.createContext("/", routerOf(model, SECRET))
        server.bind(InetSocketAddress(InetAddress.getLocalHost(), PORT), 50)
        server.start()
    }

    @BeforeEach
    fun populate() {
        model.clear()
        user = model.addUser("login", "Str0nkPassw").handleWithThrow()
    }

    @AfterAll
    fun close() {
        client.close()
        model.close()
        server.stop(1)
    }

    @Test
    fun authorize() = runBlocking {
        val (token) = client.post<AccessToken>("$API_URL/login") {
            contentType(ContentType.Application.Json)
            body = LoginPayload("login", "Str0nkPassw")
        }
        assertRight(user.id, model.verifyToken(token, SECRET))
    }

    @Test
    fun `invalid login`() = runBlocking {
        val response: HttpResponse = client.post("$API_URL/login") {
            contentType(ContentType.Application.Json)
            body = LoginPayload("invalid_login", "Str0nkPassw")
        }
        assertEquals(401, response.status.value)
        val message: RouteException = response.receive()
        assertTrue(message is RouteException.CredentialMismatch)
    }

    @Test
    fun `invalid password`() = runBlocking {
        val response: HttpResponse = client.post("$API_URL/login") {
            contentType(ContentType.Application.Json)
            body = LoginPayload("login", "Wr0ngPassword")
        }
        assertEquals(401, response.status.value)
        val message: RouteResponse = response.receive()
        assertFalse(message.ok)
        assertTrue(message is RouteException.CredentialMismatch)
    }

    @Test
    fun `invalid request`() = runBlocking {
        val response: HttpResponse = client.post("$API_URL/login") {
            body = "Wrong input"
        }
        assertEquals(400, response.status.value)
        val message: RouteResponse = response.receive()
        assertFalse(message.ok)
        assertTrue(message is RouteException.UserRequest)
    }

}