package ua.edu.ukma.csa.network

import kotlinx.serialization.SerializationException

sealed class NetworkError(msg: String) : RuntimeException(msg) {
    data class Timeout(val packetID: ULong, val tries: UInt) :
        NetworkError("Request $packetID timed out, after $tries")

    data class PacketBehind(val packetID: ULong) : NetworkError("Request $packetID is behind others")

    data class Parsing(val packetException: PacketException, val packetID: ULong) :
        NetworkError(packetException.message!!)

    data class Serialization(val serializationException: SerializationException) :
        NetworkError(serializationException.message ?: "")

    data class ServerResponse(val response: Response.Error) :
        NetworkError("Server responded with following error: $response")
}
