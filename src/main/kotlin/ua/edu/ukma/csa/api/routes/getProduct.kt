package ua.edu.ukma.csa.api.routes

import Configuration.json
import arrow.core.Either
import ua.edu.ukma.csa.api.verifyToken
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler
import java.lang.NumberFormatException

fun getProduct(model: ModelSource, tokenSecret: String): RouteHandler = fun(request): HTTPResponse {
    val token = request.headers["Authorization"]!!.first() ?: return HTTPResponse.unauthorized()
    val userID = model.verifyToken(token, tokenSecret)
    if (userID is Either.Left) return HTTPResponse.unauthorized()

    val id = try {
        request.matches["id"]!!.toInt()
    } catch (e: NumberFormatException) {
        return HTTPResponse.notFound("Invalid product id")
    }

    return when (val product = model.getProduct(ProductID(id))) {
        is Either.Left -> {
            if (product.a is ModelException.ProductDoesNotExist) HTTPResponse.notFound(product.a.message ?: "")
            HTTPResponse.serverError(product.a.message ?: "")
        }
        is Either.Right -> HTTPResponse.ok(json.stringify(Product.serializer(), product.b)).json()
    }
}