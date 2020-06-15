package ua.edu.ukma.csa.network.http

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.*
import kotlin.collections.HashMap

data class HTTPRequest(
    val matches: HashMap<String, String>,
    val headers: Headers,
    val method: String,
    val uri: URI,
    val body: InputStream
)

class HTTPResponse(
    val statusCode: Int,
    val headers: Headers = Headers(),
    val body: ByteArray = ByteArray(0)
) {
    constructor(
        statusCode: Int,
        headers: Headers = Headers(),
        body: String
    ) : this(statusCode, headers, body.toByteArray())

    companion object {
        fun ok(body: String, headers: Headers = Headers()) =
            ok(body.toByteArray(), headers)

        fun ok(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(200, headers, body)

        fun notFound(body: String, headers: Headers = Headers()) =
            notFound(body.toByteArray(), headers)

        fun notFound(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(404, headers, body)

        fun unauthorized(body: String, headers: Headers = Headers()) =
            unauthorized(body.toByteArray(), headers)

        fun unauthorized(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(401, headers, body)

        fun invalidRequest(body: String, headers: Headers = Headers()) =
            invalidRequest(body.toByteArray(), headers)

        fun invalidRequest(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(400, headers, body)

        fun serverError(body: String, headers: Headers = Headers()) =
            serverError(body.toByteArray(), headers)

        fun serverError(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(500, headers, body)
    }
}

typealias RouteHandler = (request: HTTPRequest) -> HTTPResponse

// TODO: Get rid of stringly typed HTTP methods
class HTTPMethodDefinition(
    private val handlers: HashMap<String, RouteHandler> = HashMap(),
    internal var defaultHandler: RouteHandler? = null
) {
    fun get(handler: RouteHandler) = custom("GET", handler)

    fun post(handler: RouteHandler) = custom("POST", handler)

    fun put(handler: RouteHandler) = custom("PUT", handler)

    fun patch(handler: RouteHandler) = custom("PATCH", handler)

    fun delete(handler: RouteHandler) = custom("DELETE", handler)

    fun head(handler: RouteHandler) = custom("HEAD", handler)

    fun option(handler: RouteHandler) = custom("OPTION", handler)

    fun connect(handler: RouteHandler) = custom("CONNECT", handler)

    fun default(handler: RouteHandler) {
        defaultHandler = handler
    }

    fun custom(method: String, handler: RouteHandler) {
        handlers[method] = handler
    }

    fun unite(other: HTTPMethodDefinition) = handlers.putAll(other.handlers)

    internal operator fun contains(method: String) = method in handlers
    internal operator fun get(method: String) = handlers[method]
}

val String.isSlug get() = this.startsWith(":")

val String.pathComponents: Sequence<String>
    get() {
        val refined = if (this.startsWith('/')) this.substring(1) else this
        return refined.splitToSequence('/')
    }

data class MatchedSlug(
    val definition: RouteDefinition,
    val routes: List<String>,
    val matches: HashMap<String, String>
)

sealed class TraversalMatch
data class MatchedHandler(val handler: RouteHandler, val matches: HashMap<String, String>) : TraversalMatch()
object NotFound : TraversalMatch()
data class Fork(val matches: List<MatchedSlug>) : TraversalMatch()

class RouteDefinition(private val methods: HTTPMethodDefinition = HTTPMethodDefinition()) {
    private val children = HashMap<String, RouteDefinition>()
    private val slugs = HashMap<String, RouteDefinition>()

    fun register(methods: HTTPMethodDefinition, components: List<String>, offset: Int = 0) {
        val current = components[offset]
        val source = if (current.isSlug) slugs else children
        val component = if (current.isSlug) current.substring(1) else current
        if (offset == components.size - 1) {
            val child = source[component]
            if (child == null) source[component] = RouteDefinition(methods)
            else child.methods.unite(methods)
        } else {
            val child = source.getOrPut(component) { RouteDefinition() }
            child.register(methods, components, offset + 1)
        }
    }

    fun traverse(components: List<String>, method: String, matches: HashMap<String, String>): TraversalMatch =
        if (components.isEmpty()) {
            when {
                method in methods -> MatchedHandler(methods[method]!!, matches)
                methods.defaultHandler != null -> MatchedHandler(methods.defaultHandler!!, matches)
                else -> NotFound
            }
        } else {
            val current = components.first()
            val rest = components.slice(1 until components.size)
            if (current in children) {
                children[current]!!.traverse(rest, method, matches)
            } else {
                Fork(slugs.map { (slug, definition) ->
                    matches[slug] = current
                    MatchedSlug(definition, rest, matches)
                })
            }
        }

    inline operator fun String.invoke(builder: HTTPMethodDefinition.() -> Unit): HTTPMethodDefinition {
        val methods = HTTPMethodDefinition().apply(builder)
        register(methods, this.pathComponents.map(::encodeURIComponent).map(::decodeURIComponent).toList())
        return methods
    }
}

val defaultMissingRouteHandler: RouteHandler = { request ->
    HTTPResponse.notFound("Cannot ${request.method} ${request.uri.path}")
}

// TODO: implement router composition
class Router(
    private val handleMissingRoute: Boolean = true,
    private val missingRouteHandler: RouteHandler = defaultMissingRouteHandler,
    builder: RouteDefinition.() -> Unit = {}
) : HttpHandler {
    private val definitions = RouteDefinition()

    init {
        definitions.apply(builder)
    }

    /**
     * Handle the given request and generate an appropriate response.
     * See [HttpExchange] for a description of the steps
     * involved in handling an exchange.
     * @param exchange the exchange containing the request from the
     * client and used to send the response
     * @throws NullPointerException if exchange is `null`
     */
    override fun handle(exchange: HttpExchange?) {
        exchange!!
        val request = HTTPRequest(
            matches = HashMap(),
            headers = exchange.requestHeaders,
            body = exchange.requestBody,
            method = exchange.requestMethod,
            uri = exchange.requestURI
        )

        val handler = findHandler(request.uri.path, request.method)

        val response = if (handler == null) {
            if (handleMissingRoute) missingRouteHandler(request)
            else return
        } else {
            val matched = request.copy(matches = handler.matches)
            try {
                handler.handler(matched)
            } catch (e: Exception) {
                HTTPResponse(statusCode = 500)
            }
        }

        exchange.responseHeaders.putAll(response.headers)
        if (response.body.isEmpty()) {
            exchange.sendResponseHeaders(response.statusCode, -1)
        } else {
            exchange.sendResponseHeaders(response.statusCode, response.body.size.toLong())
            exchange.responseBody.write(response.body)
        }
    }

    private fun findHandler(path: String, method: String) =
        findHandler(path.pathComponents.map(::decodeURIComponent).toList(), method)

    private fun findHandler(components: List<String>, method: String): MatchedHandler? {
        val queue = ArrayDeque<MatchedSlug>().apply {
            add(MatchedSlug(definitions, components, HashMap()))
        }
        while (queue.isNotEmpty()) {
            val (dispatcher, routes, matches) = queue.poll()
            when (val res = dispatcher.traverse(routes, method, matches)) {
                is MatchedHandler -> return res
                is Fork -> queue.addAll(res.matches)
            }
        }
        return null
    }
}

fun main() {
    val handler: RouteHandler = {
        println("response")
        HTTPResponse.ok()
    }

//    val router = Router {
//        "/api" {
//            get(handler)
//        }
//        "/api/:id" {
//            get(handler)
//        }
//        "/:slug/one" {
//            get {
//                println("/slug/one")
//                HTTPResponse.ok("response")
//            }
//        }
//        "/:another/two" {
//            get {
//                println("/another/two")
//                HTTPResponse.ok()
//            }
//        }
//        "/api/good/:id" {
//            get { request ->
//                HTTPResponse.ok(request.matches["id"]!!)
//            }
//        }
//    }

    val router = Router {
        "/hello" {
            get { HTTPResponse.ok("GET /hello") }
            post { HTTPResponse.ok("POST /hello") }
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
        "/" {
            get { HTTPResponse.ok("/") }
        }
    }

    val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 4499), 50)
    server.createContext("/", router)
    server.start()

    runBlocking {
        HttpClient(CIO).use { client ->
            println(client.get<String>("http://localhost:4499/hello"))
        }
    }
}