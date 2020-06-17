package ua.edu.ukma.csa.api

import ua.edu.ukma.csa.api.routes.login
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.http.Router

fun routerOf(model: ModelSource, tokenSecret: String) = Router {
    "/login" {
        post(login(model, tokenSecret))
    }
}