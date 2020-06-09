package ua.edu.ukma.csa.network

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.serialization.fdump
import ua.edu.ukma.csa.model.Criteria
import ua.edu.ukma.csa.model.GroupID
import ua.edu.ukma.csa.model.Ordering
import ua.edu.ukma.csa.model.ProductID

@Serializable
sealed class Request(@Transient val messageType: MessageType = MessageType.ERR) {
    @Serializable
    data class AddProduct(
        val name: String,
        var count: Int = 0,
        var price: Double,
        val groups: Set<GroupID> = emptySet()
    ) : Request(MessageType.ADD_PRODUCT)

    @Serializable
    data class GetProduct(val id: ProductID) : Request(MessageType.GET_PRODUCT)

    @Serializable
    data class GetProductList(
        val criteria: Criteria,
        val ordering: List<Ordering> = emptyList(),
        val offset: Int? = null,
        val amount: Int? = null
    ) : Request(MessageType.GET_PRODUCT_LIST)

    @Serializable
    data class RemoveProduct(val id: ProductID) : Request(MessageType.REMOVE)

    @Serializable
    data class Include(val id: ProductID, val count: Int) :
        Request(MessageType.INCLUDE)

    @Serializable
    data class Exclude(val id: ProductID, val count: Int) :
        Request(MessageType.EXCLUDE)

    @Serializable
    data class AddGroup(val name: String) : Request(MessageType.ADD_GROUP)

    @Serializable
    data class AssignGroup(val product: ProductID, val group: GroupID) :
        Request(MessageType.ASSIGN_GROUP)

    @Serializable
    data class SetPrice(val id: ProductID, val price: Double) :
        Request(MessageType.SET_PRICE)

}

inline fun <reified T : Request> T.toMessage(userID: UserID = UserID.SERVER) =
    serialize().map { Message.Decrypted(messageType, userID, message = it) }

fun <T : Request> T.toMessage(serializer: SerializationStrategy<T>, userID: UserID = UserID.SERVER) =
    serialize(serializer).map { Message.Decrypted(messageType, userID, message = it) }

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Request> T.serialize() =
    ProtoBuf.fdump(T::class.serializer(), this)

fun <T : Request> T.serialize(serializer: SerializationStrategy<T>) =
    ProtoBuf.fdump(serializer, this)
