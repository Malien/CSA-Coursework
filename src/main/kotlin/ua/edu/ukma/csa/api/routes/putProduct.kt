package ua.edu.ukma.csa.api.routes

import arrow.core.flatMap
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.RouteHandler

fun putProduct(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    val userID = model.authorizeHeaders(request.headers, tokenSecret)
    request.jsonRoute { (name, price, count, groups): PutGoodRequest ->
        userID.flatMap { _ ->
            model.addProduct(name, count, price, groups).mapLeft {
                when (it) {
                    is ModelException.ProductCanNotHaveThisPrice,
                    is ModelException.ProductCanNotHaveThisCount,
                    is ModelException.GroupsNotPresent -> RouteException.Conflict(it.message)
                    else -> RouteException.serverError(it)
                }
            }
        }.map { PushedGood(it) }
    }
}
