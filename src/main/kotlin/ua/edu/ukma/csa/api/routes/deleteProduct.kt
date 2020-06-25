package ua.edu.ukma.csa.api.routes

import arrow.core.Either
import arrow.core.flatMap
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler

fun deleteProduct(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    val userID = model.authorizeHeaders(request.headers, tokenSecret)
    if (userID is Either.Left) HTTPResponse.unauthorized()

    request.jsonRoute { (id): DeleteProductRequest->
        userID.flatMap { _ ->
            model.removeProduct(ProductID(id)).mapLeft {
                when (it) {
                    is ModelException.ProductDoesNotExist -> RouteException.NotFound(it.message)
                    else -> RouteException.serverError(it)
                }
            }
        }.map { RouteException.NoContent("No Content") }
    }
}