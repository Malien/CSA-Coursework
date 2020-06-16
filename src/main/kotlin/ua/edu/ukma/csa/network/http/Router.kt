package ua.edu.ukma.csa.network.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.util.*
import kotlin.collections.HashMap

typealias RouteHandler = (request: HTTPRequest) -> HTTPResponse

class HTTPMethodDefinition(
    private val handlers: HashMap<HTTPMethod, RouteHandler> = HashMap(),
    internal var defaultHandler: RouteHandler? = null
) {
    /** Register GET handler */
    fun get(handler: RouteHandler) = custom(HTTPMethod.GET, handler)
    /** Register POST handler */
    fun post(handler: RouteHandler) = custom(HTTPMethod.POST, handler)
    /** Register PUT handler */
    fun put(handler: RouteHandler) = custom(HTTPMethod.PUT, handler)
    /** Register PATCH handler */
    fun patch(handler: RouteHandler) = custom(HTTPMethod.PATCH, handler)
    /** Register DELETE handler */
    fun delete(handler: RouteHandler) = custom(HTTPMethod.DELETE, handler)
    /** Register HEAD handler */
    fun head(handler: RouteHandler) = custom(HTTPMethod.HEAD, handler)
    /** Register OPTIONS handler */
    fun options(handler: RouteHandler) = custom(HTTPMethod.OPTIONS, handler)

    /**
     * Register default handler.
     * Takes lower priority than method-specific handlers
     */
    fun default(handler: RouteHandler) {
        defaultHandler = handler
    }

    /** Register handler for custom HTTP method */
    fun custom(method: HTTPMethod, handler: RouteHandler) {
        handlers[method] = handler
    }

    /**
     * Combine method definitions of different handlers. If method handler is set for a particular method, then
     * that one is gonna be overridden by the new one
     */
    fun unite(other: HTTPMethodDefinition) = handlers.putAll(other.handlers)

    internal operator fun contains(method: HTTPMethod) = method in handlers
    internal operator fun get(method: HTTPMethod) = handlers[method]
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

    fun traverse(components: List<String>, method: HTTPMethod, matches: HashMap<String, String>): TraversalMatch =
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

    fun get(path: String, handler: RouteHandler) = custom(HTTPMethod.GET, path, handler)
    fun put(path: String, handler: RouteHandler) = custom(HTTPMethod.PUT, path, handler)
    fun post(path: String, handler: RouteHandler) = custom(HTTPMethod.POST, path, handler)
    fun patch(path: String, handler: RouteHandler) = custom(HTTPMethod.PATCH, path, handler)
    fun delete(path: String, handler: RouteHandler) = custom(HTTPMethod.DELETE, path, handler)
    fun head(path: String, handler: RouteHandler) = custom(HTTPMethod.HEAD, path, handler)
    fun options(path: String, handler: RouteHandler) = custom(HTTPMethod.OPTIONS, path, handler)

    fun all(path: String, handler: RouteHandler) {
        val methods = HTTPMethodDefinition(defaultHandler = handler)
        register(methods, path)
    }

    fun custom(method: HTTPMethod, path: String, handler: RouteHandler) {
        val methods = HTTPMethodDefinition(hashMapOf(method to handler))
        register(methods, path)
    }

    fun register(definition: HTTPMethodDefinition, path: String) {
        val components = path.pathComponents.map(::encodeURIComponent).map(::decodeURIComponent).toList()
        definitions.register(definition, components)
    }

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
            method = HTTPMethod.parse(exchange.requestMethod),
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

    private fun findHandler(path: String, method: HTTPMethod) =
        findHandler(path.pathComponents.map(::decodeURIComponent).toList(), method)

    private fun findHandler(components: List<String>, method: HTTPMethod): MatchedHandler? {
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
