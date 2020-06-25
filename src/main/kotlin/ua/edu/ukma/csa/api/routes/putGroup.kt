package ua.edu.ukma.csa.api.routes

import arrow.core.flatMap
import ua.edu.ukma.csa.api.*
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.RouteHandler

fun putGroup(model: ModelSource, tokenSecret: String): RouteHandler = { request ->
    val userID = model.authorizeHeaders(request.headers, tokenSecret)
    request.jsonRoute { (name): PutGroupRequest ->
        userID.flatMap {
            model.addGroup(name).mapLeft {
                if (it is ModelException.GroupAlreadyExists)
                    RouteException.Conflict("Group with name $name already exists")
                else RouteException.serverError(it)
            }
        }.map { PushedGroup(it) }
    }
}
