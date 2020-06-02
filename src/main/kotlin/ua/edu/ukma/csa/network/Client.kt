package ua.edu.ukma.csa.network

import arrow.core.Either
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import java.io.Closeable
import java.util.*

interface Client : Closeable {
    val clientID: UByte

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
        @OptIn(ImplicitReflectionSerializer::class)
        suspend inline fun <reified Req : Request, reified Res : Response> Client.fetch(
            request: Req,
            resendBehind: Boolean = true,
            retries: UInt = 0u
        ) = fetch(request, Req::class.serializer(), Res::class.serializer(), resendBehind, retries)
    }

}