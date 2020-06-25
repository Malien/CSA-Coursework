package ua.edu.ukma.csa.api.routes

import arrow.core.flatMap
import ua.edu.ukma.csa.api.GroupList
import ua.edu.ukma.csa.api.RouteException
import ua.edu.ukma.csa.api.authorizeHeaders
import ua.edu.ukma.csa.api.toJsonResponse
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.RouteHandler

fun getGroups(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    model.authorizeHeaders(request.headers, tokenSecret)
        .flatMap { model.getGroups().mapLeft { RouteException.serverError(it) } }
        .map { GroupList(it) }
        .toJsonResponse()
}