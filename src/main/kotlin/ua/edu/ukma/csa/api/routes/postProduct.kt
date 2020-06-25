package ua.edu.ukma.csa.api.routes

import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.network.http.RouteHandler


fun postProduct(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    val userID = model.authorizeHeaders(request.headers, tokenSecret)

    request.jsonRoute { updateRequest: UpdateGoodRequest ->
        userID
            .flatMap {
                try {
                    Right(request.matches["id"]!!.toInt() to updateRequest)
                } catch (e: NumberFormatException) {
                    Left(RouteException.UserRequest("Invalid product id"))
                }
            }
            .flatMap { (id, updateRequest) ->
                val (name, price, count, groups) = updateRequest
                model.updateProduct(ProductID(id), name, price, count, groups).mapLeft {
                    when (it) {
                        is ModelException.ProductCanNotHaveThisPrice,
                        is ModelException.ProductCanNotHaveThisCount,
                        is ModelException.GroupsNotPresent -> RouteException.Conflict(it.message)
                        is ModelException.ProductDoesNotExist -> RouteException.NotFound(it.message)
                        else -> RouteException.serverError(it)
                    }
                }
            }.map { RouteResponse.Ok() }
    }
}
