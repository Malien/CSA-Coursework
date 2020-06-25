package ua.edu.ukma.csa.api.routes

import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import ua.edu.ukma.csa.api.RouteException
import ua.edu.ukma.csa.api.RouteResponse
import ua.edu.ukma.csa.api.authorizeHeaders
import ua.edu.ukma.csa.api.toJsonResponse
import ua.edu.ukma.csa.model.GroupID
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.RouteHandler

fun deleteGroup(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    model.authorizeHeaders(request.headers, tokenSecret)
        .flatMap {
            try {
                Right(request.matches["id"]!!.toInt())
            } catch (e: NumberFormatException) {
                Left(RouteException.UserRequest("Invalid product id"))
            }
        }
        .flatMap { id ->
            model.removeGroup(GroupID(id)).mapLeft {
                if (it is ModelException.GroupDoesNotExist) RouteException.NotFound(it.message)
                else RouteException.serverError(it)
            }
        }
        .map { RouteResponse.Ok() }
        .toJsonResponse()
}