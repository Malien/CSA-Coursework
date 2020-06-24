package ua.edu.ukma.csa.api

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import kotlinx.serialization.*
import ua.edu.ukma.csa.Configuration.json
import ua.edu.ukma.csa.api.RouteException.Companion.serverError
import ua.edu.ukma.csa.api.routes.*
import ua.edu.ukma.csa.kotlinx.serialization.fparse
import ua.edu.ukma.csa.kotlinx.serialization.fstringify
import ua.edu.ukma.csa.model.GroupID
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.network.http.HTTPRequest
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.Router

@Serializable
sealed class RouteInput

@Serializable
sealed class RouteResponse(val ok: Boolean)

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

    @Serializable
    @SerialName("unauthorized")
    data class Unauthorized(val message: String? = null) : RouteException()

    @Serializable
    @SerialName("conflict")
    data class Conflict(val message: String? = null) : RouteException()

    fun toHTTPResponse(): HTTPResponse {
        val string = json.stringify(serializer(), this)
        return when (this) {
            is UserRequest -> HTTPResponse.invalidRequest(string)
            is CredentialMismatch -> HTTPResponse.unauthorized(string)
            is ServerError -> HTTPResponse.serverError(string)
            is Unauthorized -> HTTPResponse.unauthorized(string)
            is Conflict -> HTTPResponse.conflict(string)
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
    "/" {
        get(root)
    }
    "/api/good/:id"{
        get(getProduct(model, tokenSecret))
        delete(deleteProduct(model, tokenSecret))
    }
    "/api/good"{
        put(putProduct(model, tokenSecret))
        post(postProduct(model, tokenSecret))
    }
}

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified In : RouteInput, reified Err : RouteException, reified Res : RouteResponse> HTTPRequest.jsonRoute(
    crossinline handler: (body: In) -> Either<Err, Res>
) =
    if (headers["Content-type"]?.contains("application/json") != true)
        Left(RouteException.UserRequest("Expected content type of application/json"))
    else {
        val jsonString = String(body.readBytes())
        json.fparse(In::class.serializer(), jsonString)
            .mapLeft { RouteException.UserRequest("Cannot parse json") }
            .flatMap { handler(it) }
    }
        .flatMap { json.fstringify(Res::class.serializer(), it).mapLeft(::serverError) }
        .fold(RouteException::toHTTPResponse) { HTTPResponse.ok(it) }
        .json()

// Login route types
@Serializable
data class LoginPayload(val login: String, val password: String) : RouteInput()

@Serializable
data class AccessToken(val accessToken: String) : RouteResponse(true)

// Put product route types
@Serializable
data class PutGoodRequest(
    val name: String,
    val price: Double,
    val count: Int = 0,
    val groups: Set<GroupID> = emptySet()
) : RouteInput()


@Serializable
data class UpdateGoodRequest(
    val id: Int,
    val name: String,
    val price: Double,
    val count: Int = 0,
    val groups: Set<GroupID> = emptySet()
) : RouteInput()


@Serializable
data class PushedGood(val product: Product) : RouteResponse(true)

@Serializable
data class UpdateGood(val product: Unit) : RouteResponse(true)