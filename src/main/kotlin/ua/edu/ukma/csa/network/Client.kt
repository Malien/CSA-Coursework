package ua.edu.ukma.csa.network

import arrow.core.Either
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import java.io.Closeable
import java.util.*

/**
 * Client-server communications done via kotlin suspend functions for better convenience. To fully support all
 * client requests, implementers should implement at only [fetch]. To provide support for custom messages one can
 * extend this interface by adding functions that specialize fetch.
 */
interface Client : Closeable {

    /**
     * Make a request to the server. Sends a request to the server, suspends the caller until [Either] server responds
     * with a valid response, or a [FetchError] otherwise.
     * @param request request to be sent to the server. Subtype of [Request]
     * @param requestSerializer serialization strategy used to serialize provided message to ProtoBuf
     * @param responseDeserializer deserialization strategy used to deserialize server response from ProtoBuf. If
     * server sends message of different type to the one provided, fetch will return [Either.Left] of
     * [FetchError.Serialization]
     * @param resendBehind whether or not to automatically re-send messages that were received out of order.
     * _Defaults to `true`_
     * @param retries number of retries to be attempted when responses timeout. _Defaults to `0u`_
     * @return [Either] a [FetchError] in case of an... well... error. Or a [Res] in case of success
     */
    suspend fun <Req : Request, Res : Response> fetch(
        request: Req,
        requestSerializer: SerializationStrategy<Req>,
        responseDeserializer: DeserializationStrategy<Res>,
        resendBehind: Boolean = true,
        retries: UInt = 0u
    ): Either<FetchError, Res>

    suspend fun getQuantity(id: UUID, resendBehind: Boolean = true, retries: UInt = 0u) =
        fetch<Request.GetQuantity, Response.Quantity>(Request.GetQuantity(id), resendBehind, retries)

    suspend fun addGroup(name: String, resendBehind: Boolean = true, retries: UInt = 0u) =
        fetch<Request.AddGroup, Response.Ok>(Request.AddGroup(name), resendBehind, retries)

    suspend fun assignGroup(id: UUID, group: String, resendBehind: Boolean = true, retries: UInt = 0u) =
        fetch<Request.AssignGroup, Response.Ok>(Request.AssignGroup(id, group), resendBehind, retries)

    suspend fun setPrice(id: UUID, price: Double, resendBehind: Boolean = true, retries: UInt = 0u) =
        fetch<Request.SetPrice, Response.Ok>(Request.SetPrice(id, price), resendBehind, retries)

    suspend fun include(id: UUID, count: Int, resendBehind: Boolean = true, retries: UInt = 0u) =
        fetch<Request.Include, Response.Quantity>(Request.Include(id, count), resendBehind, retries)

    suspend fun exclude(id: UUID, count: Int, resendBehind: Boolean = true, retries: UInt = 0u) =
        fetch<Request.Exclude, Response.Quantity>(Request.Exclude(id, count), resendBehind, retries)

    companion object {
        /**
         * Convenience function to the standard fetch. Uses reified type parameters to get serializers for both
         * [request][Request] and [response][Response].
         * @sample addGroup
         * @param Req type of request to be sent. _Subtype of [Request]]_
         * @param Res type of response to be received. _Subtype of [Response]_
         * @param request request to be sent to the server
         * @param resendBehind whether or not to automatically re-send messages that were received out of order.
         * _Defaults to `true`_
         * @param retries number of retries to be attempted when responses timeout. _Defaults to `0u`_
         * @return [Either] a [FetchError] in case of an... well... error. Or a [Res] in case of success
         */
        @OptIn(ImplicitReflectionSerializer::class)
        suspend inline fun <reified Req : Request, reified Res : Response> Client.fetch(
            request: Req,
            resendBehind: Boolean = true,
            retries: UInt = 0u
        ) = fetch(request, Req::class.serializer(), Res::class.serializer(), resendBehind, retries)
    }

}