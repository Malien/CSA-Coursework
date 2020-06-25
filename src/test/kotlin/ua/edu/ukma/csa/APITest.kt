package ua.edu.ukma.csa

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.Configuration.json
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.Group
import ua.edu.ukma.csa.model.GroupID
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.SQLiteModel
import java.net.InetAddress
import java.net.InetSocketAddress

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class APITest {

    private val model = SQLiteModel(":memory:")
    private val server = HttpServer.create()
    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(JsonFeature) {
            serializer = KotlinxSerializer(json)
        }
    }

    private lateinit var token: String

    private lateinit var sweets: Group
    private lateinit var healthcare: Group
    private lateinit var dairy: Group

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    companion object {
        private const val PORT = 4499
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

        model.addUser("login", "Str0nkPassword").handleWithThrow()

        sweets = model.addGroup("Sweets").handleWithThrow()
        healthcare = model.addGroup("Healthcare").handleWithThrow()
        dairy = model.addGroup("Dairy").handleWithThrow()
        biscuit = model.addProduct("Biscuit", 10, 12.49, setOf(sweets.id)).handleWithThrow()
        conditioner = model.addProduct("Conditioner", 20, 23.69, setOf(healthcare.id)).handleWithThrow()
        iceCream = model.addProduct("Ice Cream", 5, 15.99, setOf(sweets.id, dairy.id)).handleWithThrow()

        runBlocking {
            val response: AccessToken = client.post("$API_URL/login") {
                contentType(ContentType.Application.Json)
                body = LoginPayload("login", "Str0nkPassword")
            }
            token = response.accessToken
        }
    }

    @AfterAll
    fun close() {
        client.close()
        model.close()
        server.stop(1)
    }

    @Test
    fun `GET good`() = runBlocking {
        val product: Product = client.get("$API_URL/api/good/${biscuit.id.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(biscuit, product)

        val invalidID: HttpResponse = client.get("$API_URL/api/good/invalid_id") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(400, invalidID.status.value)

        val notFound: HttpResponse = client.get("$API_URL/api/good/0") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(404, notFound.status.value)

        val unauthorized: HttpResponse = client.get("$API_URL/api/good/invalid_id")
        assertEquals(401, unauthorized.status.value)
    }

    @Test
    fun `DELETE good`() = runBlocking {
        val product: Product = client.delete("$API_URL/api/good/${iceCream.id.id}") {
            header("Authorization", "Bearer $token")
            body = DeleteProductRequest(id = 3)
        }
        assertEquals(iceCream, product)
        assertEquals(product.id, 3)

        val notFound: HttpResponse = client.delete("$API_URL/api/good/0") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = DeleteProductRequest(id = 3)
        }
        assertEquals(404, notFound.status.value)

        val unauthorized: HttpResponse = client.delete("$API_URL/api/good/invalid_id") {
            header("Content-Type", ContentType.Application.Json)
            body = DeleteProductRequest(id = 3)
        }
        assertEquals(401, unauthorized.status.value)
        assertTrue(unauthorized.receive<RouteException>() is RouteException.Unauthorized)

        val noContent: HttpResponse = client.delete("$API_URL/api/good/no_content") {
            header("Authorization", "Bearer $token")
            body = DeleteProductRequest(id = 3)
        }
        assertEquals(204, noContent.status.value)
        assertTrue(noContent.receive<RouteException>() is RouteException.NoContent)
    }

    @Test
    fun `PUT good`() = runBlocking {
        val (product) = client.put<PushedGood>("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = PutGoodRequest(name = "product", price = 12.2, count = 2, groups = setOf(sweets.id, healthcare.id))
        }
        assertEquals(product.name, "product")
        assertEquals(product.price, 12.2)
        assertEquals(product.count, 2)
        assertTrue(sweets.id in product.groups)
        assertTrue(healthcare.id in product.groups)

        val gotProduct: Product = client.get("$API_URL/api/good/${product.id.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(product, gotProduct)

        val invalidCount: HttpResponse = client.put("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = PutGoodRequest(name = "product", price = 12.2, count = -2, groups = setOf(sweets.id, healthcare.id))
        }
        assertEquals(409, invalidCount.status.value)
        assertTrue(invalidCount.receive<RouteException>() is RouteException.Conflict)

        val invalidPrice: HttpResponse = client.put("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = PutGoodRequest(name = "product", price = -12.2, count = 2, groups = setOf(sweets.id, healthcare.id))
        }
        assertEquals(409, invalidPrice.status.value)
        assertTrue(invalidPrice.receive<RouteException>() is RouteException.Conflict)

        val invalidGroups: HttpResponse = client.put("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = PutGoodRequest(name = "product", price = 12.2, count = 2, groups = setOf(GroupID(0)))
        }
        assertEquals(409, invalidGroups.status.value)
        assertTrue(invalidGroups.receive<RouteException>() is RouteException.Conflict)

        val invalidRequest: HttpResponse = client.put("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = "Invalid request"
        }
        assertEquals(400, invalidRequest.status.value)
        assertTrue(invalidRequest.receive<RouteException>() is RouteException.UserRequest)

        val unauthorized: HttpResponse = client.put("$API_URL/api/good/invalid_id") {
            header("Content-Type", ContentType.Application.Json)
            body = PutGoodRequest(name = "product", price = 12.2, count = 2)
        }
        assertEquals(401, unauthorized.status.value)
        assertTrue(unauthorized.receive<RouteException>() is RouteException.Unauthorized)
    }

    @Test
    fun `POST good`() = runBlocking {
        val (product) = client.post<UpdateGood>("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = UpdateGoodRequest(id = 1, name = "product", price = 2.99, count = 5)
        }
        assertEquals(Unit, product)

        val invalidCount: HttpResponse = client.post("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = UpdateGoodRequest(id = 1, name = "product", price = 2.99, count = 5)
        }
        assertEquals(409, invalidCount.status.value)
        assertTrue(invalidCount.receive<RouteException>() is RouteException.Conflict)

        val invalidPrice: HttpResponse = client.post("$API_URL/api/good") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = UpdateGoodRequest(id = 1, name = "product", price = 2.99, count = 5)
        }
        assertEquals(409, invalidPrice.status.value)
        assertTrue(invalidPrice.receive<RouteException>() is RouteException.Conflict)

        val notFound: HttpResponse = client.post("$API_URL/api/goodd") {
            header("Authorization", "Bearer $token")
            header("Content-Type", ContentType.Application.Json)
            body = UpdateGoodRequest(id = 1, name = "product", price = 2.99, count = 5)
        }
        assertEquals(404, notFound.status.value)

        val unauthorized: HttpResponse = client.post("$API_URL/api/good") {
            header("Content-Type", ContentType.Application.Json)
            body = UpdateGoodRequest(id = 1, name = "product", price = 2.99, count = 5)
        }
        assertEquals(401, unauthorized.status.value)
        assertTrue(unauthorized.receive<RouteException>() is RouteException.Unauthorized)

        val noContent: HttpResponse = client.delete("$API_URL/api/good/no_content") {
            header("Authorization", "Bearer $token")
            body = UpdateGoodRequest(id = 1, name = "product", price = 2.99, count = 5)
        }
        assertEquals(204, noContent.status.value)
        assertTrue(noContent.receive<RouteException>() is RouteException.NoContent)
    }


}