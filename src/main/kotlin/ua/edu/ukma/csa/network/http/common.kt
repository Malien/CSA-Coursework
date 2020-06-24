package ua.edu.ukma.csa.network.http

import com.sun.net.httpserver.Headers
import java.io.InputStream
import java.net.URI

inline class HTTPMethod(val name: String) {
    override fun toString() = name

    companion object {
        val GET = HTTPMethod("GET")
        val POST = HTTPMethod("POST")
        val PUT = HTTPMethod("PUT")
        val PATCH = HTTPMethod("PATCH")
        val DELETE = HTTPMethod("DELETE")
        val HEAD = HTTPMethod("HEAD")
        val OPTIONS = HTTPMethod("OPTIONS")

        fun parse(method: String) = when (method) {
            GET.name -> GET
            POST.name -> POST
            PUT.name -> PUT
            PATCH.name -> PATCH
            DELETE.name -> DELETE
            HEAD.name -> HEAD
            OPTIONS.name -> OPTIONS
            else -> HTTPMethod(method)
        }
    }
}

data class HTTPRequest(
    val matches: HashMap<String, String>,
    val headers: Headers,
    val method: HTTPMethod,
    val uri: URI,
    val body: InputStream
)

data class HTTPResponse(
    val statusCode: Int,
    val headers: Headers = Headers(),
    val body: ByteArray = ByteArray(0)
) {
    constructor(
        statusCode: Int,
        headers: Headers = Headers(),
        body: String
    ) : this(statusCode, headers, body.toByteArray())

    fun json(): HTTPResponse {
        headers.add("Content-Type", "application/json")
        return this
    }

    fun html(): HTTPResponse {
        headers.add("Content-Type", "text/html")
        return this
    }

    fun close(): HTTPResponse {
        headers["Connection"] = "close"
        return this
    }

    fun keepAlive(): HTTPResponse {
        headers["Connection"] = "keep-alive"
        return this
    }

    companion object {
        fun ok(body: String, headers: Headers = Headers()) =
            ok(body.toByteArray(), headers)

        fun ok(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(200, headers, body)

        fun noContent(body: String, headers: Headers = Headers()) =
            noContent(body.toByteArray(), headers)

        fun noContent(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(204, headers, body)

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

        fun conflict(body: String, headers: Headers = Headers()) =
            conflict(body.toByteArray(), headers)

        fun conflict(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(409, headers, body)

        fun serverError(body: ByteArray = ByteArray(0), headers: Headers = Headers()) =
            HTTPResponse(500, headers, body)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HTTPResponse

        if (statusCode != other.statusCode) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

