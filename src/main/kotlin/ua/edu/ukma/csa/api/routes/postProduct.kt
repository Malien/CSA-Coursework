package ua.edu.ukma.csa.api.routes

import arrow.core.flatMap
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler


fun postProduct(model: ModelSource, tokenSecret: String): RouteHandler = fun(request): HTTPResponse {
    val userID = model.authorizeHeaders(request.headers, tokenSecret)

    val id = try {
        request.matches["id"]!!.toInt()
    } catch (e: NumberFormatException) {
        return HTTPResponse.notFound("Invalid product id")
    }

    return request.jsonRoute { (id, name, price, count, groups): UpdateGoodRequest ->
        userID.flatMap { _ ->
            model.updateProduct(ProductID(id), name, price, count, groups).mapLeft {
                when (it) {
                    is ModelException.ProductCanNotHaveThisPrice,
                    is ModelException.ProductCanNotHaveThisCount,
                    is ModelException.GroupsNotPresent -> RouteException.Conflict(it.message)
                    else -> RouteException.serverError(it)
                }
            }
        }.map { UpdateGood(it) }
    }
}
