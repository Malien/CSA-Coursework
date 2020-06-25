package ua.edu.ukma.csa.api.routes

import arrow.core.Left
import arrow.core.flatMap
import ua.edu.ukma.csa.api.ProductList
import ua.edu.ukma.csa.api.RouteException
import ua.edu.ukma.csa.api.authorizeHeaders
import ua.edu.ukma.csa.api.toJsonResponse
import ua.edu.ukma.csa.model.Criteria
import ua.edu.ukma.csa.model.GroupID
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.RouteHandler
import ua.edu.ukma.csa.network.http.splitQuery

fun getProducts(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    model.authorizeHeaders(request.headers, tokenSecret)
        .flatMap {
            val queries = splitQuery(request.uri)
            val name = queries["name"]?.last()
            try {
                // TODO: add ordering, add paging
                val from = queries["from"]?.last()?.toDouble()
                val to = queries["to"]?.last()?.toDouble()
                val groups = queries["groups"]
                    ?.filterNotNull()
                    ?.map { GroupID(it.toInt()) }
                    ?.toSet()
                val criteria = Criteria(name, from, to, groups)
                model.getProducts(criteria).mapLeft {
                    if (it is ModelException.InvalidRequest)
                        RouteException.UserRequest(it.message ?: "Invalid request")
                    else RouteException.serverError(it)
                }
            } catch (e: NumberFormatException) {
                Left(
                    RouteException.UserRequest(
                        "from and to expected to be floating-point numbers. ID's in groups should be ints"
                    )
                )
            }
        }
        .map { ProductList(it) }
        .toJsonResponse()
}