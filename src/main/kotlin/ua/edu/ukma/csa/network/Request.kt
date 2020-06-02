package ua.edu.ukma.csa.network

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.java.util.UUIDSerializer
import ua.edu.ukma.csa.kotlinx.serialization.fdump
import java.util.*

@Serializable
sealed class Request(@Transient val messageType: MessageType = MessageType.ERR) {
    @Serializable
    data class GetQuantity(@Serializable(with = UUIDSerializer::class) val id: UUID) : Request(MessageType.GET_COUNT)

    @Serializable
    data class Include(@Serializable(with = UUIDSerializer::class) val id: UUID, val count: Int) :
        Request(MessageType.INCLUDE)

    @Serializable
    data class Exclude(@Serializable(with = UUIDSerializer::class) val id: UUID, val count: Int) :
        Request(MessageType.EXCLUDE)

    @Serializable
    data class AddGroup(val name: String) : Request(MessageType.ADD_GROUP)

    @Serializable
    data class AssignGroup(@Serializable(with = UUIDSerializer::class) val id: UUID, val group: String) :
        Request(MessageType.ASSIGN_GROUP)

    @Serializable
    data class SetPrice(@Serializable(with = UUIDSerializer::class) val id: UUID, val price: Double) :
        Request(MessageType.SET_PRICE)

}

inline fun <reified T : Request> T.toMessage(userID: UInt = 0u) =
    serialize().map { Message.Decrypted(messageType, userID, message = it) }

fun <T : Request> T.toMessage(serializer: SerializationStrategy<T>, userID: UInt = 0u) =
    serialize(serializer).map { Message.Decrypted(messageType, userID, message = it) }

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Request> T.serialize() =
    ProtoBuf.fdump(T::class.serializer(), this)

fun <T : Request> T.serialize(serializer: SerializationStrategy<T>) =
    ProtoBuf.fdump(serializer, this)
