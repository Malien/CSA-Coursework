package ua.edu.ukma.csa.api.routes

import Configuration.json
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.commons.codec.digest.DigestUtils
import ua.edu.ukma.csa.api.createToken
import ua.edu.ukma.csa.api.routes.RouteException.Companion.serverError
import ua.edu.ukma.csa.kotlinx.serialization.fparse
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler

@Serializable
data class LoginPayload(val login: String, val password: String)

@Serializable
sealed class RouteResponse(val ok: Boolean)

@Serializable
data class Success(val accessToken: String) : RouteResponse(true)

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

    companion object {
        fun serverError(exception: RuntimeException) = ServerError(exception.message)
    }
}

fun login(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    if (request.headers["Content-type"]?.contains("application/json") != true)
        HTTPResponse.invalidRequest("Expected content type of application/json")
    else {
        val jsonString = String(request.body.readBytes())
        json.fparse(LoginPayload.serializer(), jsonString)
            .mapLeft { RouteException.UserRequest("Cannot parse json") }
            .flatMap { (login, password) ->
                model.getUser(login)
                    .mapLeft {
                        if (it is ModelException.UserDoesNotExist) RouteException.CredentialMismatch()
                        else RouteException.ServerError(it.message)
                    }
                    .map { it to password }
            }
            .flatMap { (user, password) ->
                val hash = DigestUtils.md5Hex(password)
                if (hash == user.hash) Right(user)
                else Left(RouteException.CredentialMismatch())
            }
            .flatMap { (id) -> model.createToken(id, tokenSecret).mapLeft(::serverError) }
            .map { Success(it) }
            .map { json.stringify(Success.serializer(), it) }
            .fold({ error ->
                val string = json.stringify(RouteException.serializer(), error)
                when (error) {
                    is RouteException.UserRequest -> HTTPResponse.invalidRequest(string)
                    is RouteException.CredentialMismatch -> HTTPResponse.unauthorized(string)
                    is RouteException.ServerError -> HTTPResponse.serverError(string)
                }
            }, { HTTPResponse.ok(it) })
            .json()
            .close()
    }
}
