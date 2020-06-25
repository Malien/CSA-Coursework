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
import ua.edu.ukma.csa.model.Group
import ua.edu.ukma.csa.model.GroupID
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.network.http.HTTPRequest
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.Router

@Serializable
sealed class RouteInput

@Serializable
sealed class RouteResponse(@Required val ok: Boolean = true) {

    @Serializable
    class Ok: RouteResponse(true)
}

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
    @SerialName("notFound")
    data class NotFound(val message: String? = null): RouteException()

    @Serializable
    @SerialName("unauthorized")
    data class Unauthorized(val message: String? = null) : RouteException()

    @Serializable
    @SerialName("conflict")
    data class Conflict(val message: String? = null) : RouteException()

    @Serializable
    @SerialName("noContent")
    data class NoContent(val message: String? = null): RouteException()

    fun toHTTPResponse(): HTTPResponse {
        val string = json.stringify(serializer(), this)
        return when (this) {
            is UserRequest -> HTTPResponse.invalidRequest(string)
            is CredentialMismatch -> HTTPResponse.unauthorized(string)
            is ServerError -> HTTPResponse.serverError(string)
            is Unauthorized -> HTTPResponse.unauthorized(string)
            is Conflict -> HTTPResponse.conflict(string)
            is NoContent -> HTTPResponse.noContent(string)
            is NotFound -> HTTPResponse.notFound(string)
        }
    }

    companion object {
        fun serverError(exception: RuntimeException) = ServerError(exception.message)
    }
}

fun routerOf(model: ModelSource, tokenSecret: String) = Router {
    "/login" {
        post(login(model, tokenSecret))
        preflightOptions(allowedHeaders = listOf("Content-Type"))
    }
    "/" {
        get(root)
    }
    "/api/good/:id"{
        get(getProduct(model, tokenSecret))//done
        delete(deleteProduct(model, tokenSecret))//done
    }
    "/api/good"{
        put(putProduct(model, tokenSecret))//done
        post(postProduct(model, tokenSecret))//done
    }
    "/api/goods" {
        get(getProducts(model, tokenSecret))
        preflightOptions(allowedHeaders = listOf("Authorization"))
    }
    "/api/groups" {
        get(getGroups(model, tokenSecret))
        preflightOptions(allowedHeaders = listOf("Authorization"))
    }
    "/api/group" {
        put(putGroup(model, tokenSecret))//done
        preflightOptions(allowedHeaders = listOf("Authorization", "Content-Type"))
    }
    "/api/group/:id" {
        delete(deleteGroup(model, tokenSecret))
        preflightOptions(allowedHeaders = listOf("Authorization"))
    }
}

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified In : RouteInput, reified Res : RouteResponse> HTTPRequest.jsonRoute(
    crossinline handler: (body: In) -> Either<RouteException, Res>
) =
    if (headers["Content-type"]?.contains("application/json") != true)
        Left(RouteException.UserRequest("Expected content type of application/json"))
    else {
        val jsonString = String(body.readBytes())
        json.fparse(In::class.serializer(), jsonString)
            .mapLeft { RouteException.UserRequest("Cannot parse json") }
            .flatMap { handler(it) }
    }.toJsonResponse()

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified Res : RouteResponse> Either<RouteException, Res>.toJsonResponse() =
    flatMap { json.fstringify(Res::class.serializer(), it).mapLeft(::serverError) }
        .fold(RouteException::toHTTPResponse) { HTTPResponse.ok(it) }
        .json()

// Login route types
@Serializable
data class LoginPayload(val login: String, val password: String) : RouteInput()

@Serializable
data class AccessToken(val accessToken: String) : RouteResponse()

// Put product route types
@Serializable
data class PutGoodRequest(
    val name: String,
    val price: Double,
    val count: Int = 0,
    val groups: Set<GroupID> = emptySet()
) : RouteInput()

@Serializable
data class PushedGood(val product: Product) : RouteResponse()

// Post product route types
@Serializable
data class UpdateGoodRequest(
    val id: Int,
    val name: String,
    val price: Double,
    val count: Int = 0,
    val groups: Set<GroupID> = emptySet()
) : RouteInput()

//delete product route types
@Serializable
data class DeleteProductRequest(val id: Int) : RouteInput()

@Serializable
data class UpdateGood(val product: Unit) : RouteResponse()

// Get products route types
@Serializable
data class ProductList(val products: List<Product>) : RouteResponse()

// Get groups route types
@Serializable
data class GroupList(val groups: List<Group>): RouteResponse()

// Put group route types
@Serializable
data class PutGroupRequest(val name: String) : RouteInput()

@Serializable
data class PushedGroup(val group: Group) : RouteResponse()
