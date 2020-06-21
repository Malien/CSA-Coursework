package ua.edu.ukma.csa.api.routes

import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import org.apache.commons.codec.digest.DigestUtils
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.api.RouteException.Companion.serverError
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.RouteHandler

fun login(model: ModelSource, tokenSecret: String): RouteHandler = jsonRoute { _, (login, password): LoginPayload ->
    model.getUser(login)
        .mapLeft {
            if (it is ModelException.UserDoesNotExist) RouteException.CredentialMismatch()
            else RouteException.ServerError(it.message)
        }
        .flatMap { user ->
            val hash = DigestUtils.md5Hex(password)
            if (hash == user.hash) Right(user)
            else Left(RouteException.CredentialMismatch())
        }
        .flatMap { (id) -> model.createToken(id, tokenSecret).mapLeft(::serverError) }
        .map { AccessToken(it) }
}

