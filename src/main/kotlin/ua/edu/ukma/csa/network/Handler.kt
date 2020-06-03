package ua.edu.ukma.csa.network

import arrow.core.Either
import kotlinx.serialization.DeserializationStrategy
import kotlin.coroutines.Continuation

data class Handler<R : Response>(
    val continuation: Continuation<Either<FetchException, Response>>,
    val packet: Packet<Message.Decrypted>,
    val responseDeserializer: DeserializationStrategy<R>,
    val resendBehind: Boolean,
    val retries: UInt,
    val attempts: UInt = 0u
)

