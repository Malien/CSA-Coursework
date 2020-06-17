package ua.edu.ukma.csa.api

import Configuration.json
import arrow.core.Either
import arrow.core.flatMap
import kotlinx.serialization.*
import ua.edu.ukma.csa.api.RouteException.Companion.serverError
import ua.edu.ukma.csa.api.routes.login
import ua.edu.ukma.csa.kotlinx.serialization.fparse
import ua.edu.ukma.csa.kotlinx.serialization.fstringify
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.HTTPRequest
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler
import ua.edu.ukma.csa.network.http.Router

@Serializable
open class RouteInput

@Serializable
open class RouteResponse(val ok: Boolean)

@Serializable
sealed class RouteException : RouteResponse(false) {

    @Serializable
    @SerialName("request")
    data class UserRequest(val message: String) : RouteException()

    @Serializable
    @SerialName("credentials")
    class CredentialMismatch(@Required val message: String = "Invalid credentials") : RouteException()

    @Serializable
    @SerialName("server")
    data class ServerError(val message: String? = null) : RouteException()

    fun toHTTPResponse(): HTTPResponse {
        val string = json.stringify(serializer(), this)
        return when (this) {
            is UserRequest -> HTTPResponse.invalidRequest(string)
            is CredentialMismatch -> HTTPResponse.unauthorized(string)
            is ServerError -> HTTPResponse.serverError(string)
        }
    }

    companion object {
        fun serverError(exception: RuntimeException) = ServerError(exception.message)
    }
}

fun routerOf(model: ModelSource, tokenSecret: String) = Router {
    "/login" {
        post(login(model, tokenSecret))
    }
}

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified In: RouteInput, reified Err : RouteException, reified Res: RouteResponse> jsonRoute(
    crossinline handler: (request: HTTPRequest, body: In) -> Either<Err, Res>
): RouteHandler = { request ->
    if (request.headers["Content-type"]?.contains("application/json") != true)
        HTTPResponse.invalidRequest("Expected content type of application/json")
    else {
        val jsonString = String(request.body.readBytes())
        json.fparse(In::class.serializer(), jsonString)
            .mapLeft { RouteException.UserRequest("Cannot parse json") }
            .flatMap { handler(request, it) }
            .flatMap { json.fstringify(Res::class.serializer(), it).mapLeft(::serverError) }
            .fold(RouteException::toHTTPResponse) { HTTPResponse.ok(it) }
            .json()
            .keepAlive()
    }
}