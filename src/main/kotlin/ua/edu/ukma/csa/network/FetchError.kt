package ua.edu.ukma.csa.network

import kotlinx.serialization.SerializationException

sealed class FetchError(msg: String) : RuntimeException(msg) {
    data class Timeout(val packetID: ULong, val tries: UInt) :
        FetchError("Request $packetID timed out, after $tries")

    data class PacketBehind(val packetID: ULong) : FetchError("Request $packetID is behind others")

    data class Parsing(val packetException: PacketException, val packetID: ULong) :
        FetchError(packetException.message!!)

    data class Serialization(val serializationException: SerializationException) :
        FetchError(serializationException.message ?: "")

    data class ServerResponse(val response: Response.Error) :
        FetchError("Server responded with following error: $response")
}
