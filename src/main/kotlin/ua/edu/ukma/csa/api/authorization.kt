package ua.edu.ukma.csa.api

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.sun.net.httpserver.Headers
import ua.edu.ukma.csa.kotlinx.java.util.unixEpoch
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.model.User
import ua.edu.ukma.csa.model.UserID
import java.time.Duration
import java.time.Instant
import java.util.*

sealed class TokenException : RuntimeException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)

    class Creation(exception: JWTCreationException) :
        TokenException("Exception raised while creating token", exception)

    class Validation(exception: JWTVerificationException) :
        TokenException("Cannot validate token", exception)

    class Model(exception: ModelException) :
        TokenException("Error occurred in the model", exception)

    class InvalidToken(message: String) : TokenException(message)
}

fun ModelSource.authorizeHeaders(headers: Headers, tokenSecret: String): Either<RouteException, UserID> {
    val authHeader =
        headers["Authorization"]?.first() ?: return Left(RouteException.Unauthorized("Token is not present"))
    val split = authHeader.split(" ")
    if (split.size != 2) return Left(RouteException.Unauthorized("Invalid authorization header"))
    val (bearer, token) = split
    if (bearer.toLowerCase() != "bearer") return Left(RouteException.Unauthorized("Invalid authorization header"))
    return verifyToken(token, tokenSecret).mapLeft { RouteException.Unauthorized(it.message) }
}

fun ModelSource.createToken(
    userID: UserID,
    tokenSecret: String,
    validFor: Duration = Duration.ofDays(7)
): Either<TokenException, String> {
    val algorithm = Algorithm.HMAC256(tokenSecret)
    val expiresAt = Date.from(Instant.now() + validFor)
    return try {
        Right(
            JWT.create()
                .withIssuedAt(Date())
                .withExpiresAt(expiresAt)
                .withClaim("id", userID.id)
                .sign(algorithm)
        )
    } catch (e: JWTCreationException) {
        Left(TokenException.Creation(e))
    }.flatMap { token ->
        approveToken(token, expiresAt.unixEpoch)
            .map { token }
            .mapLeft { TokenException.Model(it) }
    }
}

fun ModelSource.verifyToken(token: String, tokenSecret: String): Either<TokenException, UserID> {
    val algorithm = Algorithm.HMAC256(tokenSecret)
    val verifier = JWT.require(algorithm)
        .acceptLeeway(5)
        .build()
    return try {
        val decoded = verifier.verify(token)
        val id = decoded.getClaim("id").asInt()
        if (id == null) Left(TokenException.InvalidToken("Token does not contain userID"))
        else Right(UserID(id))
    } catch (e: JWTVerificationException) {
        Left(TokenException.Validation(e))
    }.flatMap { userID ->
        isTokenValid(token)
            .map { userID }
            .mapLeft { TokenException.Model(it) }
    }
}

fun ModelSource.userFromToken(token: String, tokenSecret: String): Either<TokenException, User> =
    verifyToken(token, tokenSecret).flatMap { userID ->
        getUser(userID).mapLeft { TokenException.Model(it) }
    }
