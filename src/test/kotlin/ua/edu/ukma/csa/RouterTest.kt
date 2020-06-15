package ua.edu.ukma.csa

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.Router
import ua.edu.ukma.csa.network.http.encodeURIComponent
import java.net.InetAddress
import java.net.InetSocketAddress

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouterTest {

    private val client = HttpClient(CIO)
    private val server: HttpServer = HttpServer.create()

    companion object {
        private const val PORT = 4499
        private const val hostname = "localhost"
        private const val apiURL = "http://$hostname:$PORT"
    }

    init {
        val router = Router {
            "/hello" {
                get { HTTPResponse.ok("GET /hello") }
                post { HTTPResponse.ok("POST /hello") }
            }
            "/hello/:slug" {
                get { HTTPResponse.ok("GET /hello/:slug(${it.matches["slug"]})") }
            }
            "/hello/:gulag/static" {
                get { HTTPResponse.ok("GET /hello/:gulag(${it.matches["gulag"]})/static") }
            }
            "/hello/:plug/:osug" {
                get { HTTPResponse.ok("GET /hello/:plug(${it.matches["plug"]})/:osug(${it.matches["osug"]})") }
            }
            "/hello/world" {
                put { HTTPResponse.ok("PUT /hello/world") }
                default {
                    HTTPResponse(
                        statusCode = 500,
                        headers = Headers().apply { add("my-header", "my value") },
                        body = "/hello/world"
                    )
                }
            }
            "/world" {
                delete { HTTPResponse.ok("DELETE /world") }
                custom("CUSTOM") { HTTPResponse.ok("CUSTOM /world") }
            }
            "/юник🌝д" {
                get { HTTPResponse.ok("GET /юник🌝д") }
            }
            "/" {
                get { HTTPResponse.ok("/") }
            }
        }
        server.createContext("/", router)
        server.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), PORT), 50)
        server.start()
    }

    @AfterAll
    fun cleanup() {
        client.close()
        server.stop(0)
    }

    fun CoroutineScope.get(route: String) = async {
        client.get<HttpResponse>(apiURL + route)
    }

    fun CoroutineScope.put(route: String) = async {
        client.put<HttpResponse>(apiURL + route)
    }

    fun CoroutineScope.post(route: String) = async {
        client.post<HttpResponse>(apiURL + route)
    }

    fun CoroutineScope.delete(route: String) = async {
        client.delete<HttpResponse>(apiURL + route)
    }

    fun CoroutineScope.custom(methodName: String, route: String) = async {
        client.request<HttpResponse>(apiURL + route) { method = HttpMethod.parse(methodName) }
    }

    @Test
    fun `404 unspecified routes`() {
        runBlocking {
            val putHelloRequest = put("/hello")
            val postWorldRequest = post("/world")
            val getWhateverRequest = get("/whatever")
            val getHelloWorldOsugRequest = get("/hello/world/osugValue")

            val putHello = putHelloRequest.await()
            assertEquals(404, putHello.status.value)

            val postWorld = postWorldRequest.await()
            assertEquals(404, postWorld.status.value)

            val getWhatever = getWhateverRequest.await()
            assertEquals(404, getWhatever.status.value)

            val getHelloWorldOsug = getHelloWorldOsugRequest.await()
            assertEquals(404, getHelloWorldOsug.status.value)
        }
    }

    @Test
    fun `serve dynamic routes`() {
        runBlocking {
            val getHelloSlugRequest = get("/hello/slugValue")
            val getHelloGulagStaticRequest = get("/hello/gulagValue/static")
            val getHelloPlugOsugRequest = get("/hello/plugValue/osugValue")

            val getHelloSlug = getHelloSlugRequest.await()
            assertEquals(200, getHelloSlug.status.value)
            assertEquals("GET /hello/:slug(slugValue)", String(getHelloSlug.content.toByteArray()))

            val getHelloGulagStatic = getHelloGulagStaticRequest.await()
            assertEquals(200, getHelloGulagStatic.status.value)
            assertEquals(
                "GET /hello/:gulag(gulagValue)/static",
                String(getHelloGulagStatic.content.toByteArray())
            )

            val getHelloPlugOsug = getHelloPlugOsugRequest.await()
            assertEquals(200, getHelloPlugOsug.status.value)
            assertEquals(
                "GET /hello/:plug(plugValue)/:osug(osugValue)",
                String(getHelloPlugOsug.content.toByteArray())
            )

        }
    }

    @Test
    fun `serve static routes`() {
        runBlocking {
            val getHelloRequest = get("/hello")
            val postHelloRequest = post("/hello")
            val putHelloWorldRequest = put("/hello/world")
            val postHelloWorldRequest = post("/hello/world")
            val deleteWorldRequest = delete("/world")
            val customWorldRequest = custom("CUSTOM", "/world")
            val getRootRequest = get("/")
            val getUnicodeRequest = get("/${encodeURIComponent("юник🌝д")}")

            val getHello = getHelloRequest.await()
            assertEquals(200, getHello.status.value)
            assertEquals("GET /hello", String(getHello.content.toByteArray()))

            val postHello = postHelloRequest.await()
            assertEquals(200, postHello.status.value)
            assertEquals("POST /hello", String(postHello.content.toByteArray()))

            val putHelloWorld = putHelloWorldRequest.await()
            assertEquals(200, putHelloWorld.status.value)
            assertEquals("PUT /hello/world", String(putHelloWorld.content.toByteArray()))
            assertEquals(null, putHelloWorld.headers["my-header"])

            val postHelloWorld = postHelloWorldRequest.await()
            assertEquals(500, postHelloWorld.status.value)
            assertEquals("/hello/world", String(postHelloWorld.content.toByteArray()))
            assertEquals("my value", postHelloWorld.headers["my-header"])

            val deleteWorld = deleteWorldRequest.await()
            assertEquals(200, deleteWorld.status.value)
            assertEquals("DELETE /world", String(deleteWorld.content.toByteArray()))

            val customWorld = customWorldRequest.await()
            assertEquals(200, customWorld.status.value)
            assertEquals("CUSTOM /world", String(customWorld.content.toByteArray()))

            val getRoot = getRootRequest.await()
            assertEquals(200, getRoot.status.value)
            assertEquals("/", String(getRoot.content.toByteArray()))

            val getUnicode = getUnicodeRequest.await()
            assertEquals(200, getUnicode.status.value)
            assertEquals("GET /юник🌝д", String(getUnicode.content.toByteArray()))
        }
    }
}