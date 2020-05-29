package ua.edu.ukma.csa.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.serialization.fdump
import ua.edu.ukma.csa.kotlinx.util.UUIDSerializer
import java.util.*

@Serializable
sealed class Response(@Transient internal val messageType: MessageType = MessageType.OK) {
    @Serializable
    data class Error(val message: String): Response(MessageType.ERR)

    @Serializable
    object Ok: Response()

    @Serializable
    data class Quantity(@Serializable(with = UUIDSerializer::class) val id: UUID, val count: Int): Response()

    @Serializable
    data class Price(@Serializable(with = UUIDSerializer::class) val id: UUID, val count: Double): Response()

}

fun <T: Response> T.toMessage(userID: Int = 0, serializer: KSerializer<T>) =
    serialize(serializer).map { Message.Decrypted(messageType, userID, message = it) }

fun <T: Response> T.serialize(serializer: KSerializer<T>) =
    ProtoBuf.fdump(serializer, this)
