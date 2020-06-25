package ua.edu.ukma.csa.api.routes

import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import ua.edu.ukma.csa.api.RouteException
import ua.edu.ukma.csa.api.RouteResponse
import ua.edu.ukma.csa.api.authorizeHeaders
import ua.edu.ukma.csa.api.toJsonResponse
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.network.http.RouteHandler

fun deleteProduct(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    model.authorizeHeaders(request.headers, tokenSecret)
        .flatMap {
            try {
                Right(request.matches["id"]!!.toInt())
            } catch (e: NumberFormatException) {
                Left(RouteException.UserRequest("Invalid product id"))
            }
        }
        .flatMap { id ->
            model.removeProduct(ProductID(id)).mapLeft {
                when (it) {
                    is ModelException.ProductDoesNotExist -> RouteException.NotFound(it.message)
                    else -> RouteException.serverError(it)
                }
            }
        }
        .map { RouteResponse.Ok() }
        .toJsonResponse()
}