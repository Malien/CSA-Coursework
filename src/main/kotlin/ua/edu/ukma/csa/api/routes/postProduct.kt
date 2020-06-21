package ua.edu.ukma.csa.api.routes

import kotlinx.serialization.Serializable
import ua.edu.ukma.csa.Configuration.json
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler

@Serializable
data class UpdateProduct(val name: String? = null, val price: Double? = null, val count: Int? = null)

fun postProduct(model: ModelSource): RouteHandler = fun(request): HTTPResponse {

    val changeRequest = "{\"price\" : \"setPrice\"}"
    json.parse(UpdateProduct.serializer(), changeRequest)

    return HTTPResponse.noContent("No Content")
}