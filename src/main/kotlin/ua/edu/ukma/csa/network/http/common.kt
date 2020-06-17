package ua.edu.ukma.csa.network.http

import com.sun.net.httpserver.Headers
import java.io.InputStream
import java.net.URI

inline class HTTPMethod(val name: String) {
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

