package ua.edu.ukma.csa

import arrow.core.Either
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import com.sun.net.httpserver.HttpServer
import ua.edu.ukma.csa.api.routerOf
import ua.edu.ukma.csa.api.serve
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.network.tcp.TCPServer
import ua.edu.ukma.csa.network.udp.UDPServer
import java.net.InetAddress
import java.net.InetSocketAddress

class Serve : CliktCommand(help = "Launch the server", name = "serve") {
    private val dbname by option(
        "--database-url", "--database", "-d",
        help = "Path to the database",
        envvar = "DATABASE_URL",
        metavar = "URL"
    ).required()

    private val adminLogin: String? by option(
        "--admin-login", "--admin",
        help = "Create model with admin account with specified login",
        envvar = "ADMIN_LOGIN"
    ).validate { adminPassword != null }

    private val adminPassword: String? by option(
        "--admin-password", "--password",
        help = "Password of the admin account",
        envvar = "ADMIN_PASSWORD"
    ).validate { adminLogin != null }

    val model by findObject<ModelSource> { SQLiteModel(dbname) }

    init {
        subcommands(HTTP(), TCP(), UDP())
    }

    override fun run() {
        model
        if (adminLogin != null && adminPassword != null) {
            val existing = model.getUser(adminLogin!!)
            println(
                when {
                    existing is Either.Right -> "User with an admin login already exists"
                    existing is Either.Left && existing.a is ModelException.UserDoesNotExist -> {
                        val admin = model.addUser(adminLogin!!, adminPassword!!).handleWithThrow()
                        "Created admin account with id ${admin.id}"
                    }
                    else -> "Couldn't create an admin account"
                }
            )
        }
    }

    class HTTP : CliktCommand(help = "Launch HTTP REST API server", name = "http") {
        private val port by option(
            "--port", "-p",
            help = "Port which will be used to serve requests",
            envvar = "PORT",
            metavar = "INT"
        ).int().default(80)

        private val bindAddress by option(
            "--bind", "-b",
            help = "Address to which underlying TCP socket will bind to",
            envvar = "BIND_ADDRESS"
        ).default("0.0.0.0")

        private val tokenSecret by option(
            "--token-secret",
            help = "Secret which will be used to sign access tokens",
            envvar = "TOKEN_SECRET"
        ).required()

        private val backlog by option(
            "--backlog",
            help = "Set the system TCP backlog queue"
        ).int().default(50)

        private val model by requireObject<ModelSource>()

        override fun run() {
            val router = routerOf(model, tokenSecret)
            val server = HttpServer.create(InetSocketAddress(bindAddress, port), backlog)
            server.createContext("/", router)
            server.start()
        }
    }

    class TCP : CliktCommand(help = "Launch TCP API server", name = "tcp") {
        private val port by option(
            "--port", "-p",
            help = "Port which will be used to serve requests",
            envvar = "PORT"
        ).int().default(80)

        private val bindAddress by option(
            "--bind", "-b",
            help = "Address to which TCP socket will bind to",
            envvar = "BIND_ADDRESS"
        ).default("0.0.0.0")

        private val backlog by option(
            "--backlog",
            help = "Set the system TCP backlog queue"
        ).int().default(50)

        private val model by requireObject<ModelSource>()

        // TODO: Add encryption support.
        // TODO: Add key-exchange
        override fun run() {
            val server = TCPServer(port, backlog, InetAddress.getByName(bindAddress))
            server.serve(model)
        }
    }

    class UDP : CliktCommand(help = "Launch UDP API server", name = "udp") {
        private val port by option(
            "--port", "-p",
            help = "Port which will be used to serve requests",
            envvar = "PORT"
        ).int().default(80)

        private val bindAddress by option(
            "--bind", "-b",
            help = "Address to which TCP socket will bind to",
            envvar = "BIND_ADDRESS"
        ).default("0.0.0.0")

        private val model by requireObject<ModelSource>()

        // TODO: Add encryption support.
        // TODO: Add key-exchange
        override fun run() {
            val server = UDPServer(port, InetAddress.getByName(bindAddress))
            server.serve(model)
        }
    }
}

fun main(args: Array<String>) = Serve().main(args)