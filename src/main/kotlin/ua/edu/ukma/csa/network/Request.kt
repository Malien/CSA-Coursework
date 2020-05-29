package ua.edu.ukma.csa.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.serialization.fdump
import ua.edu.ukma.csa.kotlinx.util.UUIDSerializer
import java.util.*

@Serializable
sealed class Request(@Transient internal val messageType: MessageType = MessageType.ERR) {
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

fun <T: Request> T.toMessage(userID: Int = 0, serializer: KSerializer<T>) =
    serialize(serializer).map { Message.Decrypted(messageType, userID, message = it) }

fun <T: Request> T.serialize(serializer: KSerializer<T>) =
    ProtoBuf.fdump(serializer, this)
