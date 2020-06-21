package ua.edu.ukma.csa.api.routes

import org.intellij.lang.annotations.Language
import ua.edu.ukma.csa.network.http.HTTPResponse
import ua.edu.ukma.csa.network.http.RouteHandler

val root: RouteHandler = {
    @Language("HTML")
    val page = """<html lang="en"><head><title>Product Store API</title></head><body><h1>Index page</h1></body></html>"""
    HTTPResponse.ok(page).html()
}