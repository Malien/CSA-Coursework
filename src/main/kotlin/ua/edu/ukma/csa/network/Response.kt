package ua.edu.ukma.csa.network

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.serialization.fdump
import ua.edu.ukma.csa.kotlinx.util.UUIDSerializer
import java.util.*

@Serializable
sealed class Response(@Transient val messageType: MessageType = MessageType.OK) {
    @Serializable
    data class Error(val message: String) : Response(MessageType.ERR)

    @Serializable
    object Ok : Response()

    @Serializable
    data class Quantity(@Serializable(with = UUIDSerializer::class) val id: UUID, val count: Int) : Response()

    @Serializable
    data class Price(@Serializable(with = UUIDSerializer::class) val id: UUID, val count: Double) : Response()

}

inline fun <reified T : Response> T.toMessage(userID: Int = 0) =
    serialize().map { Message.Decrypted(messageType, userID, message = it) }

fun <T : Response> T.toMessage(userID: Int = 0, serializer: KSerializer<T>) =
    serialize(serializer).map { Message.Decrypted(messageType, userID, message = it) }

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Response> T.serialize() =
    ProtoBuf.fdump(T::class.serializer(), this)

fun <T : Response> T.serialize(serializer: KSerializer<T>) =
    ProtoBuf.fdump(serializer, this)

