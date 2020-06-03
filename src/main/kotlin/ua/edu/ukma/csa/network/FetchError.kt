package ua.edu.ukma.csa.network

import kotlinx.serialization.SerializationException

/**
 * Class that signifies errors in clint-server communication. (Mostly using [Client] APIs)
 */
sealed class FetchError(msg: String) : RuntimeException(msg) {

    /**
     * Signifies a request timeout
     * @param packetID id of a packet that has been timed out
     * @param tries number of retries attempted
     */
    data class Timeout(val packetID: ULong, val tries: UInt) :
        FetchError("Request $packetID timed out, after $tries")

    /**
     * Signifies that packets weren't able to be received in-order
     * @param packetID id of a packet that was sent out of order
     */
    data class PacketBehind(val packetID: ULong) : FetchError("Request $packetID is behind others")

    /**
     * Signifies an error while parsing either a [Packet] or a [Message] received from the server
     * @param packetException exception that was raised while trying to parse packet
     */
    data class Parsing(val packetException: PacketException) :
        FetchError(packetException.message!!)

    /**
     * Signifies an error while serializing user request or deserializing server response. Possible causes include:
     * message being received in incompatible format, request and request serializers mismatch or server response and
     * response deserializer mismatch.
     * @param serializationException exact exception that was raised in the process of (de)serialization
     */
    data class Serialization(val serializationException: SerializationException) :
        FetchError(serializationException.message ?: "")

    /**
     * Signifies a error sent back by the server.
     * @param response exact response message sent back by the server
     */
    data class ServerResponse(val response: Response.Error) :
        FetchError("Server responded with following error: $response")
}
