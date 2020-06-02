package ua.edu.ukma.csa.network

import arrow.core.Either
import kotlinx.serialization.DeserializationStrategy
import java.io.Closeable

interface Client : Closeable {
    val clientID: UByte

    suspend fun <R: Response> request(
        message: Message.Decrypted,
        responseDeserializer: DeserializationStrategy<R>,
        resendBehind: Boolean = true,
        retries: UInt = 0u
    ): Either<NetworkError, R>
}